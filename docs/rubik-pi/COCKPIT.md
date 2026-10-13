# Cockpit: managing the Rubik Pi from a browser

## What is Cockpit?

Cockpit is a Linux management web interface. It displays system health, services and logs, and provides a Terminal. The separate **cockpit-files** application adds a Files page for uploading/downloading and managing files.

It is not PhotonVision: opening Cockpit does not configure a camera pipeline. Think of Cockpit as a dashboard for the computer and PhotonVision as the application that processes camera images.

| Interface | Reference lab URL | Purpose |
|---|---|---|
| PhotonVision | `http://10.10.106.72:5800/` | Cameras, calibration, pipelines, vision settings |
| Cockpit | `https://10.10.106.72:9090/` | Files, system services, logs, Terminal |

Replace the address with the Pi's current address. Keep management access on the approved lab/robot network; do not port-forward Cockpit to the Internet.

## Our verified installation

Nathan installed Cockpit on the reference Rubik Pi. A read-only inspection confirmed:

| Component | Observed value |
|---|---|
| OS | Ubuntu 24.04.3 LTS, codename **noble**, ARM64 |
| Cockpit/core packages | `362-1~bpo24.04.1` from `noble-backports` |
| Files application | `cockpit-files`, version `39-1~bpo24.04.1` |
| Package mirror | `http://ports.ubuntu.com/ubuntu-ports` |
| APT source | `/etc/apt/sources.list.d/ubuntu.sources` |
| Enabled regular suites | `noble noble-updates noble-backports` |
| Components | `main universe restricted multiverse` |
| Security suite | `noble-security`, in a separate stanza |
| Service entry point | `cockpit.socket`, enabled and active, listening on port 9090 |

Package versions change over time; use `apt-cache policy` rather than expecting these exact versions forever. This guide documents the installed setup; it is not an instruction to reinstall an already working Pi.

## Why did we need noble-backports?

An APT **repository** is a software catalogue. Ubuntu's release codename is `noble`; `noble-backports` contains selected newer applications made available for Ubuntu 24.04 without upgrading the entire operating system.

Cockpit is also available in the base Ubuntu release. **Backports was needed for the newer Cockpit/file-management setup used here**, not because every Cockpit installation universally requires backports. Cockpit's official Ubuntu instructions recommend backports for current versions.

`apt install -t noble-backports ...` means "select these packages and their dependencies with noble-backports as the target release." It is not the same as changing the Pi to a newer Ubuntu release. Backports have different testing/security-maintenance arrangements from the base release; keep this mentor-managed and do not select backports for an indiscriminate full-system upgrade.

## Installation reference — mentor approval required

These commands change packages/services. Use them only on a Pi that needs setup, after checking its OS and getting approval. No reboot or OS release upgrade is part of these instructions.

**Run on the Rubik Pi, not the build computer:**

```bash
. /etc/os-release
printf 'OS codename: %s\n' "$VERSION_CODENAME"
dpkg --print-architecture
```

Expected for this guide: `noble` and `arm64`. For another release/architecture, use its appropriate repositories rather than copying these settings blindly.

### 1. Check the package sources

```bash
sudoedit /etc/apt/sources.list.d/ubuntu.sources
```

This opens an editor; it does not itself choose what to change. In the existing regular Ubuntu stanza, confirm that `Suites:` includes `noble-backports` and `Components:` includes `universe`. Leave `Signed-By`, the security stanza and other existing settings intact. The relevant lines in our ARM64 setup are:

```text
URIs: http://ports.ubuntu.com/ubuntu-ports
Suites: noble noble-updates noble-backports
Components: main universe restricted multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
```

This snippet is **not a complete replacement source file**. Do not remove `noble-security`, add duplicate source entries, use Debian backports, or substitute an x86 Ubuntu mirror. If the sources are already correct, exit without changes.

The installed image notes that cloud-init generated `ubuntu.sources`; customizations may not survive reprovisioning/rebundling. Record them in the team's setup notes and ask a mentor about persistence instead of making unrelated cloud-init changes.

### 2. Refresh the catalogue and preview installation

```bash
sudo apt update
apt-cache policy cockpit cockpit-files
sudo apt-get --simulate install -t noble-backports cockpit cockpit-files
```

`apt update` refreshes the package catalogue; it does not upgrade every installed application. `--simulate` previews additions/upgrades/removals. Review unexpected changes with a mentor.

### 3. Install and enable the management socket

```bash
sudo apt install -t noble-backports cockpit cockpit-files
sudo systemctl enable --now cockpit.socket
```

`cockpit.socket` listens for connections and starts the web service when needed (**socket activation**). This is why `cockpit.service` can be idle while the socket is healthy. `enable` makes the socket start at boot; `--now` starts it immediately.

For a later approved targeted update, preview first and use the same `-t noble-backports` selection. Avoid running a broad `apt upgrade` from the software page during robot testing.

### 4. Verify

```bash
dpkg-query -W cockpit cockpit-files
systemctl is-enabled cockpit.socket
systemctl status cockpit.socket --no-pager
```

Then open `https://PI_ADDRESS:9090/` from a browser on the same approved network. The socket check proves a listener is running; browsing and logging in proves the interactive path works.

## Login and HTTPS safety

- Cockpit uses a **Linux system account**. Use the mentor-provided account (our example is `photon`) and its approved password. SSH-key access and browser login are separate: adding a key to `authorized_keys` does not log you into Cockpit.
- Do not put passwords, SSH private keys or browser cookies in Git, screenshots, tickets or student worksheets.
- A newly installed Cockpit instance may use a self-signed certificate. A browser warning can be expected, but verify the device address and certificate with a mentor before accepting it. Do not train yourself to click through unexpected certificate changes.
- Use administrative access only when needed. Cockpit's Terminal can run real commands and its management pages can make real system changes; it is not a sandbox.

## Student workflow: upload a JAR and inspect PhotonVision

1. Log into Cockpit and open **Files** (provided by `cockpit-files`).
2. Go to `/home/photon`, then upload the mentor-approved Linux ARM64 JAR. Keep its descriptive filename.
3. Do **not** overwrite `/opt/photonvision/photonvision.jar` while the service is running.
4. Open **Terminal**, run the updater's `--dry-run`, compare its checksum, and get activation approval. Follow [Updating PhotonVision](PHOTONVISION_UPDATE.md).
5. Use **Services** to find `photonvision.service` and inspect status/logs. UI wording may differ by Cockpit version.
6. Reopen the separate PhotonVision interface to check cameras, pipelines and robot connectivity.

The Files page is useful for staging and downloading evidence. Prefer the updater for installation because it coordinates stop, backup, replacement and start. Avoid ad hoc drag-and-drop into protected system directories.

**Never change networking, storage, package versions or reboot the Pi from Cockpit during a match or without the programming lead's approval.**

## Troubleshooting

| Symptom | Read-only checks / next step |
|---|---|
| Page unreachable | Confirm current IP/network; check `systemctl status cockpit.socket`; ask a mentor before changing firewall rules |
| Files page missing | Check `dpkg-query -W cockpit-files`; installation requires approval |
| Login fails | Confirm the correct system account; do not repeatedly guess credentials |
| Permission denied under `/opt` | Expected for normal user access; stage in the home folder and use the approved updater |
| Service shows failed | Read its journal; do not mask the error by rebooting repeatedly |
| Backports target missing | Confirm Ubuntu codename/source stanza and refresh package metadata; do not add an unrelated distribution repository |

Official references: [Cockpit installation](https://cockpit-project.org/running.html#ubuntu), [Cockpit applications](https://cockpit-project.org/applications.html), [Ubuntu Backports](https://help.ubuntu.com/community/UbuntuBackports).
