# Secret Calculator — Private Photo Vault (Android)

**Package:** `com.secretcalculator.vault`  
**Minimum SDK:** API 26 (Android 8.0 Oreo) · **Target SDK:** API 35 (Android 15)  
**Architecture:** Kotlin + Jetpack Compose + MVVM + Room + Android Keystore (`AES/GCM/NoPadding`) + Coil

---

## 1. Security Architecture Summary

| Layer | Mechanism |
| --- | --- |
| **Vault Master Key** | Hardware-backed `AndroidKeyStore` 256-bit AES-GCM key (`com.secretcalculator.vault.master_aes_key`) — never leaves Keystore. |
| **Photo File Encryption** | Each imported photo and thumbnail gets a fresh 96-bit random IV prepended to AES-256-GCM ciphertext (`[12B IV][Ciphertext + 128-bit GCM Tag]`) in `filesDir/vault_blobs/`. |
| **Zero Plaintext Temp Files** | Decryption happens strictly in memory (`ByteArray`) and renders into Compose `ImageBitmap`; no temporary unencrypted files are written to cache or external storage. |
| **PIN Security** | Salted `PBKDF2WithHmacSHA256` (120,000 iterations) stored inside `EncryptedSharedPreferences` (`AES256_SIV` keys / `AES256_GCM` values) and verified in constant time (`MessageDigest.isEqual`). |
| **Screen & Backup Protection** | `FLAG_SECURE` prevents screenshots and Recents previews while the vault is open; `android:allowBackup="false"` and `data_extraction_rules.xml` block Android cloud backups. |
| **Offline Guarantee** | `android.permission.INTERNET` is intentionally omitted from `AndroidManifest.xml`. |
| **Secure Deletion** | Permanent deletion overwrites encrypted blob files with random bytes and zeroes via `RandomAccessFile("rws")` before unlinking. |
| **Encrypted Backups** | `.scvault` archives are encrypted with user passphrase (`PBKDF2WithHmacSHA256` 200,000 rounds + AES-256-GCM). Losing the backup passphrase makes recovery mathematically impossible. |

---

## 2. Project Structure

```
android-project/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── drawable/ic_splash_calculator.xml
        │   ├── mipmap-anydpi-v26/ic_launcher*.xml
        │   ├── values/{colors,strings,themes}.xml
        │   └── xml/data_extraction_rules.xml
        └── java/com/secretcalculator/vault/
            ├── SecretCalculatorApp.kt
            ├── MainActivity.kt
            ├── security/{CryptoManager,PinManager}.kt
            ├── data/
            │   ├── local/{VaultDatabase,dao/*,entity/*}.kt
            │   ├── preferences/VaultPreferences.kt
            │   ├── backup/VaultBackupModels.kt
            │   └── repository/VaultRepository.kt
            └── ui/
                ├── theme/{Color,Theme,Type}.kt
                ├── onboarding/PinSetupScreen.kt
                ├── calculator/{CalculatorViewModel,CalculatorScreen}.kt
                ├── vault/{VaultViewModel,VaultScreen,PhotoViewerScreen,TrashScreen}.kt
                ├── settings/SettingsScreen.kt
                └── navigation/AppNavGraph.kt
```

---

## 3. Building the APK in Android Studio

1. Open **Android Studio Ladybug (2024.2+)** and select **File -> Open...** -> choose the `android-project/` directory.
2. Let Gradle sync all dependencies (`libs.versions.toml` uses AGP `8.7.3`, Kotlin `2.1.0`, Compose BOM `2024.12.01`, Room `2.6.1`).
3. To build a **Debug APK**:
   - GUI: **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**
   - CLI:
     ```bash
     ./gradlew assembleDebug
     ```
     Output: `app/build/outputs/apk/debug/app-debug.apk`
4. To build a **Signed Release APK**:
   - GUI: **Build -> Generate Signed Bundle / APK... -> APK** -> create/select your `.jks` upload keystore.
   - CLI:
     ```bash
     ./gradlew assembleRelease
     ```
5. Install via ADB on a physical device or Android 8.0+ emulator:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 4. Setup & Verification Test Checklist

1. **First-Launch PIN Setup**: Launch app -> create a 4–8 digit PIN -> confirm PIN -> verify calculator opens.
2. **Normal Calculator Behavior**: Test `25 + 17 =`, `100 ÷ 0 =` (`Error`), `%`, `±`, `.`, `AC`, `⌫`, and History sheet. Enter an incorrect PIN and press `=` — confirm it stays in Calculator mode with zero vault hints.
3. **Secret Vault Unlock**: Enter your exact PIN -> press `=` -> verify Private Vault opens and calculator state resets cleanly.
4. **Encrypted Photo Import & Viewer**: Tap **Import Photos** -> select multiple gallery images -> confirm grid thumbnails load, tap for full-screen pinch-zoom, inspect file info (`AES-256-GCM`).
5. **Albums & Trash**: Create an album, rename it, move photos into it, soft-delete a photo to Trash, restore it, then permanently wipe a trashed photo.
6. **Auto-Lock & Screenshot Block**: With vault open, press Home / switch apps -> return to app -> verify it automatically locks back to the Calculator. Attempt a screenshot inside the vault to confirm `FLAG_SECURE` blocks capture.
7. **Encrypted Backup & Restore**: Settings -> Export Encrypted Backup (`.scvault`) -> wipe/delete a photo -> Restore with Secret PIN + Backup Passphrase -> confirm photo returns.
8. **Unit Tests**: Run `./gradlew testDebugUnitTest` to execute `CalculatorEngineTest`.
