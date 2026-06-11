val colors = listOf("#0D0D0D", "#00FF41", "#151515", "#001F08", "#FFB300", "#00BFFF", "package:")
colors.forEach { 
    val xored = it.map { (it.code xor 0x77).toByte() }.toByteArray()
    println(java.util.Base64.getEncoder().encodeToString(xored))
}
