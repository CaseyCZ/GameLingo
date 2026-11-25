package cz.caseycz.mlkitlab;

import android.content.Context;
import android.content.res.AssetManager;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OcrBenchmarkTest {
    private static final String[] FILTERS={"raw","gray","contrast","binary","white","outline","adaptive","darkbox","sharpen"};
    private static final int[] SCALES={75,125,150,200};
    private static final int[] TARGETS={16,20,24,32,40};
    private static final String TAG="MLKitLab";
    private static double now(){return SystemClock.elapsedRealtimeNanos()/1e6;}
    private static String read(AssetManager assets,String name)throws Exception{
        try(InputStream in=assets.open(name); ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            return out.toString("UTF-8");
        }
    }
    private static Bitmap bitmap(AssetManager assets,String id,String filter)throws Exception{
        try(InputStream in=assets.open(id+"_"+filter+".png")){
            Bitmap b=BitmapFactory.decodeStream(in);
            if(b==null)throw new IOException("Cannot decode "+id+" "+filter);
            return b;
        }
    }
    private static Text recognize(TextRecognizer engine,Bitmap bitmap)throws Exception{
        return Tasks.await(engine.process(InputImage.fromBitmap(bitmap,0)),40,TimeUnit.SECONDS);
    }
    private static List<Text.Line> lines(Text result){
        List<Text.Line> out=new ArrayList<>();
        for(Text.TextBlock block:result.getTextBlocks())out.addAll(block.getLines());
        return out;
    }
    // Experimental center/height rule; no text dictionary or knowledge of expected text.
    private static String geometry(Text result,int width,int height){
        List<Text.Line> candidates=new ArrayList<>();
        for(Text.Line line:lines(result)){
            Rect box=line.getBoundingBox();
            if(box!=null && box.height()>0 && box.centerY()>=height*.25 &&
               Math.abs(box.centerX()-width*.5)<=width*.27)candidates.add(line);
        }
        int max=0;for(Text.Line line:candidates)max=Math.max(max,line.getBoundingBox().height());
        final int ref=max;
        candidates.removeIf(line->line.getBoundingBox().height()<ref*.55);
        candidates.sort(Comparator.comparingInt((Text.Line line)->line.getBoundingBox().top)
                                  .thenComparingInt(line->line.getBoundingBox().left));
        List<String> out=new ArrayList<>();for(Text.Line line:candidates)out.add(line.getText());
        return String.join("\n",out);
    }
    private static String confidence(Text result){
        List<String> out=new ArrayList<>();
        for(Text.Line line:lines(result)){
            // Bundled SDK reports confidence; zero also covers unavailable estimates.
            if(line.getConfidence()>=.60f)out.add(line.getText());
        }
        return String.join("\n",out);
    }
    private static double estimate(Text result,int width,int height){
        List<Integer> heights=new ArrayList<>();
        for(Text.Line line:lines(result)){
            Rect r=line.getBoundingBox();
            if(r!=null && r.height()>0 && r.centerY()>=height*.25 &&
               Math.abs(r.centerX()-width*.5)<=width*.27){
                for(Text.Element element:line.getElements()){
                    Rect e=element.getBoundingBox();
                    if(e!=null && e.height()>0)heights.add(e.height());
                }
            }
        }
        if(heights.isEmpty())return 0;
        Collections.sort(heights);return heights.get(heights.size()/2);
    }
    private static void emit(PrintWriter out,JSONObject row)throws Exception{
        out.println(row.toString());out.flush();
        if(out.checkError())throw new IOException("Could not write report");
    }
    private static Text measure(TextRecognizer engine,Bitmap original,JSONObject sample,
        String config,double scale,double probeMs,PrintWriter out)throws Exception{
        double start=now();
        Bitmap b=original;
        try{
            if(scale!=1)b=Bitmap.createScaledBitmap(original,Math.max(1,(int)Math.round(original.getWidth()*scale)),
                Math.max(1,(int)Math.round(original.getHeight()*scale)),true);
            double ocrStart=now();
            Text text=recognize(engine,b);
            double finish=now();
            JSONObject row=new JSONObject();
            row.put("id",sample.getString("id"));row.put("dataset",sample.getString("dataset"));
            row.put("scene",sample.getString("scene"));row.put("expected",sample.getString("expected"));
            row.put("config",config);row.put("scale",scale);row.put("actual",text.getText());
            row.put("geometry",geometry(text,b.getWidth(),b.getHeight()));
            row.put("confidence",confidence(text));row.put("ocr_ms",finish-ocrStart);
            row.put("resize_ms",ocrStart-start);row.put("probe_ms",probeMs);
            emit(out,row);
            return text;
        }finally{if(b!=original)b.recycle();}
    }
    @Test public void benchmark()throws Exception{
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        AssetManager assets=InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        JSONArray manifest=new JSONArray(read(assets,"manifest.json"));
        assertEquals(72,manifest.length());
        File file=new File(target.getFilesDir(),"results.jsonl");
        TextRecognizer engine=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8))){
            JSONObject smoke=manifest.getJSONObject(6);
            Bitmap first=bitmap(assets,smoke.getString("id"),"raw");
            try{
                double start=now();
                Text t=recognize(engine,first);
                Log.i(TAG,"SMOKE "+t.getText()+" ms="+(now()-start));
                assertTrue("Real ML Kit OCR smoke failed: "+t.getText(),
                    t.getText().toLowerCase(Locale.ROOT).contains("one chance"));
            }finally{first.recycle();}
            // Each fixture shares its exact pixels across the candidate configurations.
            // Probe text is reused for experiment efficiency; charge its latency to every auto variant.
            for(int i=0;i<manifest.length();i++){
                JSONObject sample=manifest.getJSONObject(i);
                String id=sample.getString("id");
                for(String filter:FILTERS){
                    Bitmap im=bitmap(assets,id,filter);
                    try{
                        double start=now();
                        Text probe=measure(engine,im,sample,filter+"_100",1,0,out);
                        double probeMs=now()-start;
                        if(filter.equals("raw")||filter.equals("outline")){
                            for(int scale:SCALES)measure(engine,im,sample,filter+"_"+scale,scale/100.0,0,out);
                        }
                        if(filter.equals("raw")){
                            double estimated=estimate(probe,im.getWidth(),im.getHeight());
                            for(int size:TARGETS){
                                double scale=estimated>0?Math.max(.75,Math.min(2.0,size/estimated)):1.0;
                                measure(engine,im,sample,"auto_"+size,scale,probeMs,out);
                            }
                        }
                    }finally{im.recycle();}
                }
                Log.i(TAG,"FRAME "+(i+1)+"/"+manifest.length());
            }
            Log.i(TAG,"COMPLETE "+manifest.length()+" frames");
        }finally{engine.close();}
    }
}
