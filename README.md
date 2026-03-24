# DialogAuth

A modern, secure authentication system for Minecraft servers using native dialog windows.

![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_64h.png) ![github](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_64h.png)

![fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_64h.png) soon on forge and neoforge

## Features

✨ **Native Dialog Interface** - Clean, intuitive authentication using Minecraft's built-in dialog system

🔒 **Secure Password Storage** - Passwords are hashed using BCrypt (cost factor 12) for maximum security

⏱️ **Smart Session Management** - Players stay logged in for 12 hours (configurable)

🌐 **IP-Based Security** - Automatic re-authentication when IP address changes

🎮 **Seamless Experience** - Players spawn in a void dimension during authentication, then return to their exact location

⚙️ **Fully Configurable** - Customize session duration, password requirements, and more

## Screenshots

<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/2471204b35ee149f51763fd27fa0106673652dc2.jpeg" width="30%" alt="Registration Dialog"/>
  <img src="https://cdn.modrinth.com/data/cached_images/dd349758c015d40cc1724b5a5609356fa85732a4.jpeg" width="30%" alt="Login Dialog"/>
  <img src="https://cdn.modrinth.com/data/cached_images/ec521f22af45a0ca1924d234561bd328d7209edb.jpeg" width="30%" alt="Change Password Dialog"/>
</p>

## How It Works

### First Time Players
1. Player joins the server
2. Teleported to authentication dimension (void space)
3. Registration dialog appears
4. Enter password twice to confirm
5. Automatically returned to spawn location

### Returning Players
- **Within 12 hours + Same IP**: Instant login, no dialog
- **After 12 hours OR Different IP**: Login dialog appears
- Enter password to authenticate
- Return to your saved location

## Commands

- `/dialogauth changepass` - Change your password
- `/dialogauth reload` - Reload configuration (admin only)

## Configuration

Located in `config/dialogauth/config.json`:

```json
{
  "authentication": {
    "min_password_length": 4,
    "max_password_length": 32,
    "session_duration_hours": 12,
    "check_ip_address": true
  }
}
```

### Key Settings

- `session_duration_hours` - How long players stay logged in (default: 12 hours)
- `check_ip_address` - Require re-login when IP changes (default: true)
- `min_password_length` - Minimum password length (default: 4)

## Localization

Customize all messages in `config/dialogauth/lang.json`:

```json
{
  "command": {
    "register": {
      "success": "§aSuccessfully registered!"
    },
    "leave": {
      "disconnect_message": "Disconnected"
    }
  }
}
```

## Dialog Customization

All dialogs can be customized in `config/dialogauth/dialogs/`:
- `register/` - Registration dialogs
- `login/` - Login dialogs  
- `changepass/` - Password change dialogs

**Note**: Dialog changes require server restart.

## Security Features

✅ BCrypt password hashing (industry standard)

✅ No plaintext passwords stored

✅ Session-based authentication

✅ IP address verification

✅ Configurable password requirements

✅ Protected authentication dimension

## Requirements

- Minecraft 1.21.6+
- Fabric Loader
- Fabric API

## Installation

1. Download and install [Fabric Loader](https://fabricmc.net/use/)
2. Download [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download DialogAuth from [Modrinth](https://modrinth.com/mod/dialog-auth)
4. Place both mods in your server's `mods` folder
5. Start your server
6. Configuration files will be auto-generated in `config/dialogauth/`

## Support

Found a bug or have a suggestion? Open an issue on GitHub!

---

**Made with ❤️ for the Minecraft community**
