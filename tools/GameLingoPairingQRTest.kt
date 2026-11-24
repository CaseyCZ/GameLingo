package cz.titulky.tv
import org.junit.Assert.*
import org.junit.Test
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.BinaryBitmap
import com.google.zxing.common.HybridBinarizer

class GameLingoPairingQRTest {
    @Test fun validatesLocalPayloadAndRoundTripsRealQr() {
        val raw = GameLingoPairingQR.encode("192.168.1.12", "123456")!!
        assertEquals(GameLingoPairingQR("192.168.1.12", "123456"), GameLingoPairingQR.parse(raw))
        val matrix = QRCodeWriter().encode(raw, BarcodeFormat.QR_CODE, 360, 360)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(
            RGBLuminanceSource(matrix.width, matrix.height, pixels))))
        assertEquals(raw, decoded.text)
        for (invalid in listOf("https://evil.example", "GLPAIR2|192.168.1.2|123456",
            "GLPAIR1|8.8.8.8|123456", "GLPAIR1|127.0.0.1|123456",
            "GLPAIR1|192.168.01.2|123456", "GLPAIR1|192.168.1.2|１２３４５６",
            "GLPAIR1|192.168.1.2|123456|secret", "GLPAIR1|192.168.1.255|123456",
            "GLPAIR1|192.168.1.2:41989|123456", "GLPAIR1|192.168.1.2|12345")) {
            assertNull(GameLingoPairingQR.parse(invalid))
        }
    }
}
