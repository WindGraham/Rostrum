# Xterm Terminal Assets

The xterm WebView renderer loads only packaged assets under:

```text
app/src/main/assets/terminal/
```

The required entrypoint is:

```text
app/src/main/assets/terminal/terminal.html
```

Do not load xterm assets from a CDN or any external URL. The WebView host blocks navigation and subresource requests outside `file:///android_asset/terminal/`.

## Vendored Assets

The current packaged runtime assets are copied from npm tarballs:

```text
@xterm/xterm@6.0.0
integrity: sha512-TQwDdQGtwwDt+2cgKDLn0IRaSxYu1tSUjgKarSDkUM0ZNiSRXFpjxEsvc/Zgc5kq5omJ+V0a8/kIM2WD3sMOYg==

@xterm/addon-fit@0.11.0
integrity: sha512-jYcgT6xtVYhnhgxh3QgYDnnNMYTcf8ElbxxFzX0IZo+vabQqSPAjC3c1wJrKB5E19VwQei89QCiZZP86DCPF7g==
```

Runtime files live under:

```text
app/src/main/assets/terminal/vendor/xterm.js
app/src/main/assets/terminal/vendor/xterm.css
app/src/main/assets/terminal/vendor/addon-fit.js
```

The copied MIT license files live beside them:

```text
app/src/main/assets/terminal/vendor/xterm.LICENSE
app/src/main/assets/terminal/vendor/addon-fit.LICENSE
```

## Kotlin Contract

The Kotlin side uses `XtermTerminalContract`:

```kotlin
ASSET_URL = "file:///android_asset/terminal/terminal.html"
JS_BRIDGE_NAME = "RostrumTerminal"
JS_API_NAME = "RostrumXterm"
```

## JavaScript Bridge

`terminal.html` must call the Kotlin bridge after xterm has mounted:

```javascript
window.RostrumTerminal.ready();
```

Terminal input must be sent to Kotlin with:

```javascript
window.RostrumTerminal.input(data);
```

Paste data must be sent with:

```javascript
window.RostrumTerminal.paste(data);
```

Resize events must be sent with terminal columns and rows:

```javascript
window.RostrumTerminal.resize(columns, rows);
```

## JavaScript API Exposed By Assets

The Kotlin controller expects `terminal.html` to expose:

```javascript
window.RostrumXterm.fit();
window.RostrumXterm.write(base64Data);
```

`write(base64Data)` receives raw PTY output encoded as base64. The asset code is responsible for decoding it and writing the bytes/text to xterm.js.

## Current State

`terminal.html` is packaged and implements this contract. If it is removed or not packaged, `XtermTerminalPane` shows a diagnostic instead of starting the WebView renderer.
