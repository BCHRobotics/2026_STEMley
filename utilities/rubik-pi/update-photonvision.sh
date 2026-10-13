#!/usr/bin/env bash
# Replace PhotonVision's JAR without changing its service or Java configuration.
# Usage: ./update-photonvision.sh [--dry-run] /path/to/photonvision-linuxarm64.jar
set -Eeuo pipefail

dry_run=false
if [[ ${1:-} == --dry-run ]]; then dry_run=true; shift; fi
if [[ $# != 1 || ! -f $1 || $1 != *.jar ]]; then
    printf 'Usage: %s [--dry-run] /path/to/photonvision-linuxarm64.jar\n' "$0" >&2
    exit 2
fi
source_jar=$(realpath -- "$1")
app_dir=/opt/photonvision
target_jar="$app_dir/photonvision.jar"
backup_root=/opt/photonvision-backups
service=photonvision.service
java_bin=/usr/lib/jvm/java-25-openjdk-arm64/bin/java

[[ -f $target_jar && -x $java_bin ]] || { printf 'Installed JAR or Java 25 is missing.\n' >&2; exit 1; }
[[ $source_jar != "$target_jar" ]] || { printf 'Source must not be the running JAR.\n' >&2; exit 2; }
# Check ZIP integrity, entry point, Java compatibility, and ARM64 native resources.
python3 - "$source_jar" <<'PY'
import sys, zipfile, struct
with zipfile.ZipFile(sys.argv[1]) as jar:
    # Shadow JARs can repeat identical central-directory records sharing one local
    # file entry. Python 3.12 assigns those records a zero-length overlap boundary.
    # Recompute boundaries from DISTINCT offsets, rejecting conflicting aliases;
    # then read every exact entry to verify decompression and CRC, not by filename.
    entries = jar.infolist()
    offsets = sorted({entry.header_offset for entry in entries} | {jar.start_dir})
    bounds = dict(zip(offsets, offsets[1:]))
    seen = {}
    for entry in entries:
        identity = (entry.filename, entry.CRC, entry.file_size, entry.compress_size, entry.compress_type)
        prior = seen.setdefault(entry.header_offset, identity)
        if prior != identity:
            raise SystemExit('Conflicting ZIP records share a file offset')
        entry._end_offset = bounds[entry.header_offset]
        with jar.open(entry) as stream:
            while stream.read(1024 * 1024):
                pass
    manifest = jar.read('META-INF/MANIFEST.MF').decode()
    if 'Main-Class: org.photonvision.Main' not in manifest:
        raise SystemExit('Not a PhotonVision application JAR')
    major = struct.unpack('>H', jar.read('org/photonvision/Main.class')[6:8])[0]
    if major > 69:
        raise SystemExit('JAR requires a Java version newer than installed Java 25')
    if not any(n.startswith('linux/arm64/shared/') and '.so' in n for n in jar.namelist()):
        raise SystemExit('JAR does not contain Linux ARM64 native libraries')
PY
source_hash=$(sha256sum -- "$source_jar" | cut -d ' ' -f1)
printf 'Source: %s\nDestination: %s\nSHA256: %s\n' "$source_jar" "$target_jar" "$source_hash"
if $dry_run; then
    printf 'DRY RUN: would back up JAR/configuration, stop %s, replace JAR, and start service. No changes made.\n' "$service"
    exit 0
fi
if (( EUID != 0 )); then
    exec sudo -- bash "$(realpath -- "$0")" "$source_jar"
fi
# Prevent two simultaneous updates and protect backups from other users.
umask 077
exec 9>/run/lock/photonvision-update.lock
flock -n 9 || { printf 'Another PhotonVision update is running.\n' >&2; exit 1; }
mkdir -p -- "$backup_root"
backup=$(mktemp -d "$backup_root/update-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX")
staged=$(mktemp "$app_dir/.photonvision.jar.XXXXXX")
stopped=false
recover_on_error() {
    local rc=$?
    trap - ERR
    printf 'Update failed. Backup: %s\n' "$backup" >&2
    [[ ! -f $staged ]] || rm -f -- "$staged"
    # If failure occurred after stopping, try to start the currently installed JAR.
    # Do not silently roll back a JAR against a potentially migrated database.
    if $stopped; then systemctl start "$service" || true; fi
    exit "$rc"
}
trap recover_on_error ERR
install -o root -g root -m 0644 -- "$source_jar" "$staged"
[[ $(sha256sum -- "$staged" | cut -d ' ' -f1) == "$source_hash" ]]
systemctl cat "$service" > "$backup/service-before.txt"
systemctl stop "$service"
stopped=true
# Archive the stopped application's configuration, including a consistent SQLite DB.
tar -czf "$backup/photonvision-before.tar.gz" -C /opt photonvision
tar -tzf "$backup/photonvision-before.tar.gz" >/dev/null
cp -p -- "$target_jar" "$backup/photonvision.jar"
# Atomic rename ensures a failed copy cannot leave a partially written installed JAR.
mv -f -- "$staged" "$target_jar"
systemctl start "$service"
stopped=false
trap - ERR
printf 'Started %s. Backup: %s\n' "$service" "$backup"
# Require the same service process to stay up for five seconds after HTTP is ready.
for (( attempt=0; attempt<30; attempt++ )); do
    if systemctl is-active --quiet "$service" && curl -fsS --max-time 2 http://127.0.0.1:5800/api/status >/dev/null; then
        pid=$(systemctl show "$service" -p MainPID --value)
        sleep 5
        if systemctl is-active --quiet "$service" && [[ $pid == "$(systemctl show "$service" -p MainPID --value)" ]]; then
            sha256sum -- "$target_jar"
            printf 'Verified: stable service process and HTTP status endpoint.\n'
            printf 'Check cameras, pipelines and robot connection in http://10.10.106.72:5800/\n'
            exit 0
        fi
    fi
    sleep 1
done
printf 'Startup verification failed; inspect: sudo journalctl -u %s -n 80\n' "$service" >&2
printf 'Backup: %s. A rollback may require restoring configuration as well as the JAR.\n' "$backup" >&2
exit 1
