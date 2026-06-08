const fs = require('fs');
const path = require('path');

const files = ['app/src/main/java/com/kittyspace/KittyDumpService.kt', 'app/src/main/java/com/kittyspace/dumper/KittyDumperEngine.kt'];

function obfStr(s) {
    const x = Buffer.from(s).map(b => b ^ 0x77);
    return `com.kittyspace.ui.Obfuscator.o("${x.toString('base64')}")`;
}

files.forEach(file => {
    if (!fs.existsSync(file)) return;
    let c = fs.readFileSync(file, 'utf8');
    let m = false;

    const rLog = /(?:addLog|onLog\s*\(?|Log\.d\([^,]+,\s*)\s*"([^"\n]+)"/g;
    let match;
    while ((match = rLog.exec(c)) !== null) {
        if (!match[1].includes('Obfuscator')) {
            c = c.replace(`"${match[1]}"`, obfStr(match[1]));
            m = true;
        }
    }

    if (m) {
        fs.writeFileSync(file, c);
        console.log('Obfuscated ' + file);
    }
});
