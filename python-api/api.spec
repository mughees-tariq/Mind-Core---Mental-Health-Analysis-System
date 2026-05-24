# PyInstaller spec for Mental Health Python API
# Run: pyinstaller api.spec

import os
block_cipher = None

a = Analysis(
    ['app.py'],
    pathex=['.'],
    binaries=[],
    datas=[
        ('model.safetensors', '.'),
        ('tokenizer.json', '.'),
        ('tokenizer_config.json', '.'),
        ('classes.npy', '.'),
        ('config.json', '.'),
        ('.env', '.'),
    ],
    hiddenimports=[
        'transformers',
        'torch',
        'flask',
        'flask_cors',
        'numpy',
        'sklearn',
        'google.generativeai',
        'dotenv',
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=['matplotlib', 'PIL', 'tkinter'],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='mental-health-api',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=True,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='mental-health-api',
)
