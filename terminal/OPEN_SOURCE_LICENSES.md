# Open Source Licenses for terminal module

This module contains the following third-party open source components:

## PRoot (libproot.so, libloader.so)
- **License**: GNU General Public License v2 (GPL-2.0)
- **Source**: https://github.com/proot-me/proot
- **Usage**: User-space chroot implementation for running Linux rootfs on Android without root
- **Compliance**: PRoot is distributed as a standalone binary (mere aggregation per GPL FAQ).
  OmniMaster does not link against or modify PRoot source code.
  PRoot source code is available at the URL above.

## talloc (liblibtalloc.so.2.so)
- **License**: GNU Lesser General Public License v3 (LGPL-3.0)
- **Source**: https://www.samba.org/ftp/talloc/
- **Usage**: Memory allocation library required by PRoot
- **Compliance**: Distributed as a shared library (.so). Users may replace this library
  with their own build. Source is available at the URL above.

## Bash (libbash.so)
- **License**: GNU General Public License v3 (GPL-3.0)
- **Source**: https://ftp.gnu.org/gnu/bash/
- **Usage**: Shell interpreter for the Linux environment
- **Compliance**: Distributed as a standalone binary (mere aggregation).
  Source code is available at the URL above.

## BusyBox (libbusybox.so)
- **License**: GNU General Public License v2 (GPL-2.0)
- **Source**: https://busybox.net/
- **Usage**: Provides basic Unix command-line utilities
- **Compliance**: Distributed as a standalone binary (mere aggregation).
  Source code is available at the URL above.

## Ubuntu Rootfs
- **License**: Various (Ubuntu packages maintain their own licenses)
- **Source**: https://github.com/proot-distro/proot-distro
- **Usage**: Ubuntu Noble (24.04) aarch64 base filesystem for the PRoot environment
- **Distribution**: proot-distro v4.18.0

---

For GPL compliance, source code for all GPL-licensed components can be obtained
from the URLs listed above. If you have questions about licensing, please contact
the project maintainers.
