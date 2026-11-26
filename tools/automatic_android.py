"""Automatic native ML Kit mode. Applied after both variants' other patches."""
from pathlib import Path

def install_automatic(folder, tv):
    def one(text,old,new):
        if text.count(old)!=1:
            raise RuntimeError("Automatic OCR anchor: "+old[:100]+" count "+str(text.count(old)))
        return text.replace(old,new,1)
    for name in ("GameLingoAutomaticTracker.kt","GameLingoAutomaticConfig.kt","GameLingoAutomaticOCR.kt","GameLingoZonePacket.kt"):
        (folder/name).write_text(Path(__file__).with_name(name).read_text(encoding="utf8"),encoding="utf8")
    unit_dir=folder.parents[4]/"test/java/cz/titulky/tv"
    unit_dir.mkdir(parents=True,exist_ok=True)
    (unit_dir/"GameLingoAutomaticTrackerTest.kt").write_text(Path(__file__).with_name("GameLingoAutomaticTrackerTest.kt").read_text(encoding="utf8"),encoding="utf8")
    service=folder/"RemotePlayService.kt"
    s=service.read_text(encoding="utf8")
    output = '''if (telefon) {
                    if (Propojeni.Vysilac.cilIp == null) TelefonTitulky.nastav(packet.blocks.joinToString("\\n") { it.text }, 1800)
                    else Propojeni.Vysilac.publishAutomaticZones(packet)
                }''' + (''' else {
                    OverlayService.posli(this, android.content.Intent(this, OverlayService::class.java)
                        .setAction(OverlayService.ACTION_SET_LOCAL_ZONES).putExtra(OverlayService.EXTRA_ZONE_PACKET,packet.encode()))
                }''' if tv else "")
    clear = '''if (telefon) {
            TelefonTitulky.nastav("",0)
            Propojeni.Vysilac.publishAutomaticZones(null)
        }''' + (''' else {
            OverlayService.posli(this, android.content.Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_SET_LOCAL_ZONES).putExtra(OverlayService.EXTRA_ZONE_PACKET,"PCZ1\\t1.7778\\t1\\t0"))
        }''' if tv else "")
    methods='''    @Volatile private var automaticReader: GameLingoAutomaticOCR? = null
    private val automaticLock = Any()
    private fun automaticFrame(w:Int,h:Int,readPixels:(Int,Int,Int,Int)->IntArray): Boolean {
        if(!GameLingoAutomaticConfig.enabled(this))return false
        if(!bezi||pozastaveno)return true
        val reader=synchronized(automaticLock) {
            automaticReader?.takeIf{it.alive()} ?: run {
                automaticReader?.close()
                val session=relaceCteni.get()
                val language=GameLingoLanguages.revision
                val output=Propojeni.Vysilac.outputGeneration()
                GameLingoAutomaticOCR(this,GameLingoAutomaticConfig.zones(this),
                    {bezi&&!pozastaveno&&relaceCteni.get()==session&&GameLingoLanguages.revision==language&&Propojeni.Vysilac.outputGeneration()==output},
                    {packet ->
                '''+output+'''
                    }).also{automaticReader=it}
            }
        }
        reader.offer(w,h,readPixels)
        return true
    }
    private fun clearAutomaticReader() {
        val previous=synchronized(automaticLock){automaticReader.also{automaticReader=null}}
        if(previous==null)return
        previous.close()
        '''+clear+'''
    }
    private fun automaticXbox(f:org.webrtc.VideoFrame):Boolean =
        automaticFrame(f.rotatedWidth,f.rotatedHeight){x,y,w,h->
            val i=f.buffer.toI420()?:error("I420")
            try{
                IntArray(w*h){p->
                    val xx=x+p%w;val yy=y+p/w
                    val (sx,sy)=when(f.rotation){
                        90 -> yy to (i.height-1-xx)
                        180 -> (i.width-1-xx) to (i.height-1-yy)
                        270 -> (i.width-1-yy) to xx
                        else -> xx to yy
                    }
                    val l=i.dataY.get(sy*i.strideY+sx).toInt() and 255
                    (255 shl 24) or (l shl 16) or (l shl 8) or l
                }
            }finally{i.release()}
        }
'''
    s=one(s,"    private fun snimekXbox(f: org.webrtc.VideoFrame) {",methods+"\n    private fun snimekXbox(f: org.webrtc.VideoFrame) {\n        if(automaticXbox(f))return")
    s=one(s,"                    potvrdObraz(true, relaceObrazu)\n",'''                    potvrdObraz(true, relaceObrazu)
                    if(automaticFrame(gl.sirka,gl.vyska){x,y,w,h->gl.precti(x,y,w,h)})return@snimek
''')
    s=one(s,"                        potvrdObraz(false, relaceObrazu)\n",'''                        potvrdObraz(false, relaceObrazu)
                        if(GameLingoAutomaticConfig.enabled(this)) {
                            try{
                                automaticFrame(img.width,img.height){x,y,w,h->
                                    val p=img.planes[0];val data=p.buffer.duplicate()
                                    IntArray(w*h){index->
                                        val l=data.get((y+index/w)*p.rowStride+(x+index%w)*p.pixelStride).toInt() and 255
                                        (255 shl 24) or (l shl 16) or (l shl 8) or l
                                    }
                                }
                            }finally{img.close()}
                            return@let
                        }
''')
    s=one(s,"    private fun zastav(zprava: String, ukazat: Boolean = true) {","    private fun zastav(zprava: String, ukazat: Boolean = true) {\n        clearAutomaticReader()")
    s=one(s,"    override fun onDestroy() {","    override fun onDestroy() {\n        clearAutomaticReader()")
    old="private fun pozadujePribeh() = "+("" if tv else "telefon && ")+"nast.pribehovy && nast.dialogy"
    s=one(s,old,old+" && !GameLingoAutomaticConfig.enabled(this)")
    service.write_text(s,encoding="utf8")
    transport=folder/"Propojeni.kt"
    p=transport.read_text(encoding="utf8")
    p=one(p,"        fun outputGeneration(): Long = generace","""        fun outputGeneration(): Long = generace
        private var automaticPacket: GameLingoZonePacket? = null
        private var automaticAt = 0L
        internal fun publishAutomaticZones(packet: GameLingoZonePacket?) {
            synchronized(zamek) {
                automaticPacket=packet
                automaticAt=android.os.SystemClock.elapsedRealtime()
                if(packet==null)posliData { "PCZ1\\t1.7778\\t1\\t0".toByteArray(Charsets.UTF_8) }
            }
        }""")
    # The single existing sender thread owns all snapshot datagrams and their generation.
    p=one(p,"                cilIp = ip\n","                automaticPacket=null;automaticAt=0L\n                cilIp = ip\n")
    p=one(p,"            // Invalidate already queued senders, then send a new clear snapshot.\n","            automaticPacket=null;automaticAt=0L\n            // Invalidate already queued senders, then send a new clear snapshot.\n")
    p=one(p,'            posliTed()\n            posliData { zpravaVoleb() }\n            oblastTitulku','            posliTed()\n            posliData { "PCZ1\\t1.7778\\t1\\t0".toByteArray(Charsets.UTF_8) }\n            posliData { zpravaVoleb() }\n            oblastTitulku')
    p=one(p,'                                    if (repeatDialogue) {\n                                        val data = zprava()', '''                                    if (repeatDialogue) {
                                        val packet=automaticPacket
                                        val current=if(packet!=null && android.os.SystemClock.elapsedRealtime()-automaticAt<=2000L)packet
                                            else if(packet!=null)GameLingoZonePacket(16f/9f,1f,emptyList()) else null
                                        val data = if(current==null)zprava() else if(GameLingoTVPairing.supportsZones(ip))
                                            current.encode().toByteArray(Charsets.UTF_8) else current.legacy(++cislo)''')
    p=one(p,'"$OBLAST\\t0\\t0.1\\t0.1\\t0.9\\t0.9"\n','"$OBLAST\\t0\\t0.1\\t0.1\\t0.9\\t0.9",\n                "PCZ1\\t1.7778\\t1\\t0"\n')
    transport.write_text(p,encoding="utf8")
