package cz.caseycz.mlkitlab;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CombinationTest {
    @Test public void combination() throws Exception {
        var inst=InstrumentationRegistry.getInstrumentation();
        var assets=inst.getContext().getAssets();
        JSONArray samples;
        try(InputStream in=assets.open("combination.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buf=new byte[4096]; int n;
            while((n=in.read(buf))!=-1)bytes.write(buf,0,n);
            samples=new JSONArray(bytes.toString("UTF-8"));
        }
        assertEquals(24,samples.length());
        var engine=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        File result=new File(inst.getTargetContext().getFilesDir(),"combination.jsonl");
        try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(result),StandardCharsets.UTF_8))){
            for(int i=0;i<samples.length();i++){
                JSONObject sample=samples.getJSONObject(i);
                for(String mode:(i%2==0?new String[]{"raw","outline"}:new String[]{"outline","raw"})){
                    JSONObject row=new JSONObject(sample.toString());
                    row.put("engine","mlkit-android"); row.put("mode",mode);
                    JSONArray lines=new JSONArray();
                    Bitmap im;
                    try(InputStream in=assets.open(sample.getString("id")+"_"+mode+".png")){im=BitmapFactory.decodeStream(in);}
                    assertNotNull(im);
                    long start=SystemClock.elapsedRealtimeNanos();
                    try {
                        Text read=Tasks.await(engine.process(InputImage.fromBitmap(im,0)),20,TimeUnit.SECONDS);
                        for(Text.TextBlock block:read.getTextBlocks())for(Text.Line line:block.getLines()){
                            Rect b=line.getBoundingBox();if(b==null)continue;
                            JSONObject l=new JSONObject();
                            l.put("text",line.getText());l.put("box",new JSONArray(new int[]{b.left,b.top,b.right,b.bottom}));
                            lines.put(l);
                        }
                    } catch(Exception e){row.put("error",e.toString());}
                    finally{im.recycle();}
                    row.put("ocr_ms",(SystemClock.elapsedRealtimeNanos()-start)/1e6);row.put("lines",lines);
                    out.println(row);out.flush();if(out.checkError())throw new IOException("Write failed");
                    Log.i("ComboOCR","COMBO "+row);
                }
            }
        }finally{engine.close();}
    }
}
