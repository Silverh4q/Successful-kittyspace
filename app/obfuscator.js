const colors = ["#0D0D0D", "#00FF41", "#151515", "#001F08", "#FFB300", "#00BFFF", "package:", "KittySpy", "Please grant Display over other apps permission for the Mod Menu to work", "SYS.TERMINAL // "];

colors.forEach(str => {
    let xored = Buffer.from(str).map(b => b ^ 0x77);
    console.log(`"${str}" -> "${xored.toString('base64')}"`);
});
