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
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainActivity extends AppCompatActivity {

    private Bitmap  imgBitmap;
    private boolean foundImage;
    private File    imgFile;
    private String  imageText;
    private Thread  workerThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("Directory",System.getProperty("user.dir"));

        ActivityResultLauncher<Intent> openFolderResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();

                            String resultText = data.getData().getPath();
                            Toast.makeText(MainActivity.this,resultText,Toast.LENGTH_LONG).show();
                            Log.d("Path",resultText);

                            File directory = new File(resultText);
                            File[] files   = directory.listFiles();
                            Log.d("Files", "Size: "+ files.length);
                            for (int i = 0; i < files.length; i++)
                            {
                                Log.d("Files", "FileName:" + files[i].getName());
                            }
                        }
                    }
                });

        final FloatingActionButton openButton = findViewById(R.id.openButton);
        openButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            public void onClick(View v) {
                workerThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ContentResolver cr = getApplicationContext().getContentResolver();
                        Uri uri = MediaStore.Images.Media.getContentUri("external");
                        String[] projection    = {MediaStore.Images.Media.DATA};
                        String[] selectionArgs = null;
                        String   sortOrder     = null;
                        String   selection     = null;

                        Cursor allImages = cr.query(uri, projection, selection, selectionArgs, sortOrder);

                        int count = allImages.getCount();

                        TessBaseAPI tess = new TessBaseAPI();
                        String datapath = Environment.getExternalStorageDirectory() + "/tesseract/";
                        String language = "eng";
                        File dir             = new File(datapath + "tessdata/");
                        InputStream trainedDataStream = getResources().openRawResource(R.raw.eng);
                        Path trainedDataPath = Paths.get(datapath + "tessdata/eng.traineddata");
                        try {
                            Files.copy(trainedDataStream,trainedDataPath);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        File trainedDataCopy = new File(datapath + "tessdata/eng.traineddata");

                        dir.mkdirs();
                        tess.init(datapath, language);

                        TextView imageTextView = (TextView) findViewById(R.id.imageText);
                        imageText       = imageTextView.getText().toString();
                        imageText = imageText.toLowerCase();
                        Log.d("ImageText",imageText);
                        ImageView imgView = (ImageView) findViewById(R.id.imageView);

                        if (allImages != null) {
                            while (allImages.moveToNext()) {
                                int columnIndex = allImages.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                                String path     = allImages.getString(columnIndex);
                                //Log.d("Path",path);
                                //Toast.makeText(getApplicationContext(),path,Toast.LENGTH_LONG);

                                imgFile = new  File(path);

                                if (imgFile.exists()) {
                                    if ((path.contains("Download") || path.contains("Facebook"))) {
                                        tess.setImage(imgFile);
                                        String ocrText = tess.getUTF8Text().toLowerCase();
                                        Log.d("ocrText",ocrText);
                                        if (ocrText.contains(imageText)) {
                                            imgBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                                            //Toast.makeText(getApplicationContext(),imgFile.getName(),Toast.LENGTH_LONG);
                                            Log.i("Success","Found match: "+imgFile.getName());
                                            imgView.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    imgView.setImageBitmap(imgBitmap);
                                                }
                                            });
                                            workerThread.interrupt();
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        allImages.close();
                        Log.i("Count",count+" images found.");
                    }
                });
                workerThread.start();
            }
        });

    }

}