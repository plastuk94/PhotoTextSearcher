package com.example.phototextsearcher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private Bitmap          imgBitmap;
    private Bitmap          imgBitmap2;
    private Button          openFileButton;
    private Boolean         isImgFile1;
    private ContentResolver cr;
    private Cursor          allImages;
    private Cursor          allImages2;
    private File            imgFile;
    private File            imgFile2;
    private ImageView       imgView;
    private AtomicInteger   imageCount;
    private TextView        progressView;
    private Semaphore       semThread2Ready;
    private String          datapath;
    private String          imageText;
    private String[]        projection;
    private TessBaseAPI     tess;
    private TessBaseAPI     tess2;
    private Thread          workerThread; //TODO: Make the Runnable in these into an actual class,
    private Thread          workerThread2;//TODO: so I can create as many threads as needed.
    private Uri             uri;

    public MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("Directory",System.getProperty("user.dir"));

        final FloatingActionButton openButton = findViewById(R.id.openButton);
        openButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            public void onClick(View v) {

                semThread2Ready = new Semaphore(1);
                try {
                    semThread2Ready.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cr = getApplicationContext().getContentResolver();
                uri = MediaStore.Images.Media.getContentUri("external");
                projection    = new String[]{MediaStore.Images.Media.DATA};

                allImages = cr.query(uri, projection, null, null, null);

                int count  = allImages.getCount();
                int countA = (count / 2);
                int countB = (count - countA);

                tess  = new TessBaseAPI();
                tess2 = new TessBaseAPI();
                datapath = Environment.getExternalStorageDirectory() + "/tesseract/";

                workerThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        File dir             = new File(datapath + "tessdata/");
                        InputStream trainedDataStream = getResources().openRawResource(R.raw.eng);
                        Path trainedDataPath = Paths.get(datapath + "tessdata/eng.traineddata");
                        try {
                            Files.copy(trainedDataStream,trainedDataPath);
                            trainedDataStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        dir.mkdirs();
                        tess.init(datapath, "eng");

                        TextView  imageTextView = (TextView) findViewById(R.id.imageText);
                        imageText               = imageTextView.getText().toString();
                        imageText               = imageText.toLowerCase();
                        imgView                 = (ImageView) findViewById(R.id.imageView);
                        progressView            = (TextView) findViewById(R.id.progressView);
                        imageCount              = new AtomicInteger(1);

                        while (allImages.moveToNext()) {
                            int columnIndex = allImages.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                            String path     = allImages.getString(columnIndex);

                            imgFile = new  File(path);

                            if (imgFile.exists()) {
                                if ((path.contains("Download") || path.contains("Facebook"))) {
                                    progressView.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            final int i = imageCount.get();
                                            progressView.setText("Scanning image "+i+" out of "+count+".");
                                        }
                                    });

                                    tess.setImage(imgFile);
                                    String ocrText = tess.getUTF8Text().toLowerCase();
                                    Log.d("Thread #1 ocrText",ocrText+"\nCount="+count+",CountA="+countA+",countB="+countB+",imagecount="+imageCount);
                                    imageCount.incrementAndGet();
                                    semThread2Ready.release();
                                    if (ocrText.contains(imageText)) {
                                        isImgFile1 = true;
                                        allImages.close();
                                        allImages2.close();
                                        workerThread2.interrupt();
                                        imgBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                                        Log.i("Success","Found match: "+imgFile.getName());
                                        imgView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                imgView.setImageBitmap(imgBitmap);
                                                progressView.setText(progressView.getText()+
                                                        "\nFound match: "+path);
                                                openFileButton.setVisibility(View.VISIBLE);
                                            }
                                        });
                                        workerThread.interrupt();
                                        break;
                                    }
                                }
                            }
                        }
                        //allImages.close();
                        Log.i("Count",count+" images found.");
                    }
                });
                workerThread.start();
                tess2.init(datapath,"eng");
                workerThread2 = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            semThread2Ready.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        semThread2Ready.release();
                        System.out.println("Thread #2 initialized");
                        //Cursor allImages2 = allImages; //TODO clone / serialize from allImages
                        allImages2 = cr.query(uri, projection, null, null, null);
                        System.out.println("Thread #2 retrieved cursor: "+allImages2.getColumnName(0));
                        allImages2.moveToPosition(countA);

                        while (allImages2.moveToNext()) {
                            int columnIndex2 = allImages2.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                            String path2     = allImages2.getString(columnIndex2);
                            imgFile2         = new File(path2);

                            if (imgFile2.exists()) {
                                if ((path2.contains("Download") || path2.contains("Facebook"))) {
                                    tess2.setImage(imgFile2);
                                    String ocrText2 = tess2.getUTF8Text();
                                    Log.d("Thread #2 ocrText",ocrText2);
                                    imageCount.incrementAndGet();


                                    if (ocrText2.contains(imageText)) {
                                        isImgFile1 = false;
                                        allImages.close();
                                        allImages2.close();
                                        workerThread.interrupt();
                                        imgBitmap2 = BitmapFactory.decodeFile(imgFile2.getAbsolutePath());
                                        Log.i("Success","Found match: "+imgFile2.getName());

                                        imgView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                imgView.setImageBitmap(imgBitmap2);
                                                progressView.setText(progressView.getText()+
                                                        "\nFound match: "+path2);
                                                openFileButton.setVisibility(View.VISIBLE);
                                            }
                                        });
                                    }
                                }
                            }

                        }
                    }
                });
                workerThread2.start();
            }
        });

        openFileButton = (Button) findViewById(R.id.openFileButton);

        ActivityResultLauncher<Intent> openActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                        }
                    }
                });

        openFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                    Intent intent = new Intent();
                    Uri    fileUri;
                    if (isImgFile1) {
                        fileUri = FileProvider.getUriForFile(getApplicationContext(),
                                getApplicationContext().getPackageName()+".provider",
                                imgFile);
                    } else {
                        fileUri = FileProvider.getUriForFile(getApplicationContext(),
                                getApplicationContext().getPackageName()+".provider",
                                imgFile2);
                    }
                    System.out.println(fileUri.toString());
                    intent = new Intent(Intent.ACTION_VIEW,fileUri);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    openActivityResultLauncher.launch(intent);
            }
        });
    }
}