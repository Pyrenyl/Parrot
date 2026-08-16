# Parrot

Parrot mirrors notifications from a selected Android user into the current user on the same device.

It uses a daemon-mode Shizuku UserService to list full users, excluding profiles such as Work Profile and Private Space, and registers a cross-user notification listener for the selected user. The listener keeps running after the app process exits; posted notifications are recreated locally with stable tags, and removed notifications are cancelled.
