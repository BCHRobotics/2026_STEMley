import pathlib, tempfile, subprocess, os, hashlib
import argparse
parser = argparse.ArgumentParser(description='Test the updater in an isolated folder with mocked service commands; no real deployment.')
parser.add_argument('--jar', type=pathlib.Path, required=True, help='Existing Linux ARM64 PhotonVision JAR to use as a test input')
args = parser.parse_args()
script = (pathlib.Path(__file__).resolve().parents[1] / 'update-photonvision.sh').read_text()
source = args.jar.resolve()
if not source.is_file():
    parser.error('The --jar file must exist')
with tempfile.TemporaryDirectory(prefix='photon-script-test-') as tmp:
    root = pathlib.Path(tmp)
    app = root/'photonvision'; app.mkdir()
    target = app/'photonvision.jar'; target.write_bytes(b'previous-test-jar')
    (app/'photonvision_config').mkdir()
    (app/'photonvision_config'/'test.txt').write_text('preserve configuration')
    binpath=root/'bin'; binpath.mkdir()
    java=root/'java'; java.write_text('#!/bin/sh\nexit 0\n'); java.chmod(0o755)
    log=root/'calls'
    for name, text in {
        'systemctl': '#!/bin/sh\nprintf "%s\\n" "$*" >> "$TEST_CALLS"\ncase "$1" in show) printf "12345\\n";; cat) printf "TEST SERVICE\\n";; esac\n',
        'curl': '#!/bin/sh\nexit 0\n',
        'sleep': '#!/bin/sh\nexit 0\n',
        'install': '#!/usr/bin/env python3\nimport sys, shutil, os\nshutil.copyfile(sys.argv[-2], sys.argv[-1])\nos.chmod(sys.argv[-1], 0o644)\n',
    }.items():
        p=binpath/name; p.write_text(text); p.chmod(0o755)
    s=script.replace('app_dir=/opt/photonvision',f'app_dir={app}').replace('backup_root=/opt/photonvision-backups',f'backup_root={root}/backups').replace('java_bin=/usr/lib/jvm/java-25-openjdk-arm64/bin/java',f'java_bin={java}').replace('/run/lock/photonvision-update.lock',str(root/'lock')).replace('-C /opt photonvision',f'-C {root} photonvision')
    # Suppress privilege escalation only in this temporary mock-service test copy.
    s=s.replace('if (( EUID != 0 )); then', 'if false; then')
    candidate=root/'update.sh'; candidate.write_text(s)
    env=dict(os.environ,PATH=str(binpath)+':'+os.environ['PATH'],TEST_CALLS=str(log))
    r=subprocess.run(['bash',str(candidate),'--dry-run',str(source)],env=env,capture_output=True,text=True)
    assert r.returncode == 0, r.stderr
    assert target.read_bytes() == b'previous-test-jar' and not log.exists()
    print('PASS: dry run validates real ARM64 JAR without modifying target or calling systemctl')
    r=subprocess.run(['bash',str(candidate),str(root/'missing.jar')],env=env,capture_output=True,text=True)
    assert r.returncode == 2 and not log.exists()
    print('PASS: missing source rejected before service operations')
    import zipfile, struct
    damaged = root/'damaged.jar'
    blob = bytearray(source.read_bytes())
    with zipfile.ZipFile(source) as jar:
        entry = next(e for e in jar.infolist() if e.filename == 'LICENSE')
        name_len, extra_len = struct.unpack_from('<HH', blob, entry.header_offset + 26)
        position = entry.header_offset + 30 + name_len + extra_len + entry.compress_size // 2
    blob[position] ^= 0x80
    damaged.write_bytes(blob)
    r=subprocess.run(['bash',str(candidate),'--dry-run',str(damaged)],env=env,capture_output=True,text=True)
    assert r.returncode != 0 and not log.exists()
    print('PASS: damaged ZIP payload rejected before service operations')
    r=subprocess.run(['bash',str(candidate),str(source)],env=env,capture_output=True,text=True)
    assert r.returncode == 0, r.stdout+r.stderr
    assert hashlib.sha256(target.read_bytes()).digest() == hashlib.sha256(source.read_bytes()).digest()
    assert (app/'photonvision_config'/'test.txt').read_text() == 'preserve configuration'
    calls=log.read_text().splitlines()
    assert calls.index('stop photonvision.service') < calls.index('start photonvision.service')
    backups=list((root/'backups').glob('update-*'))
    assert len(backups)==1 and (backups[0]/'photonvision.jar').read_bytes()==b'previous-test-jar'
    assert (backups[0]/'photonvision-before.tar.gz').is_file()
    print('PASS: isolated replacement, stop/start sequence, backup and config preservation (mocked service/HTTP)')
print('All 4 local script checks passed; no real service operations were performed.')
