package com.example.imagerecognitiondemo;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.imagerecognitiondemo.adapter.ResultAdapter;
import com.example.imagerecognitiondemo.model.GetResult;
import com.example.imagerecognitiondemo.model.GetToken;
import com.example.imagerecognitiondemo.network.ApiService;
import com.example.imagerecognitiondemo.network.NetCallBack;
import com.example.imagerecognitiondemo.network.ServiceGenerator;
import com.example.imagerecognitiondemo.util.Base64Util;
import com.example.imagerecognitiondemo.util.Constant;
import com.example.imagerecognitiondemo.util.FileUtil;
import com.example.imagerecognitiondemo.util.SPUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ApiService service;
    private String accessToken;
    /**
     * ????????????
     */
    private ImageView ivPicture;
    /**
     *?????????
     */
    private ProgressBar pbLoading;
    /**
     * ????????????
     */
    private BottomSheetDialog bottomSheetDialog;
    /**
     * ????????????
     */
    private View bottomView;
    private RxPermissions rxPermissions;
    private File outputImage;
    /**
     * ?????????????????????
     */
    private static final int OPEN_ALBUM_CODE = 100;
    /**
     * ?????????????????????
     */
    private static final int TAKE_PHOTO_CODE = 101;
    


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        service = ServiceGenerator.createService(ApiService.class);
        getAccessToken();
        ivPicture = findViewById(R.id.iv_picture);
        pbLoading = findViewById(R.id.pb_loading);
        rxPermissions = new RxPermissions(this);
        bottomSheetDialog = new BottomSheetDialog(this);
        bottomView = getLayoutInflater().inflate(R.layout.dialog_bottom, null);
    }

    /**
     * ?????????????????????Bitmap
     * @param path ?????????????????????
     * @return
     */
    public static Bitmap openImage(String path){
        Bitmap bitmap = null;
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path));
            bitmap = BitmapFactory.decodeStream(bis);
            bis.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * ???????????????
     * @param imageBitmap
     * @param keywordRects
     * @param valueRects
     */
    private void drawRectangles(Bitmap imageBitmap, int[] keywordRects,
                                int[] valueRects) {
        int left, top, right, bottom;
        Bitmap mutableBitmap = imageBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
//Canvas canvas = new Canvas(imageBitmap);
        Paint paint = new Paint();
        for (int i = 0; i < 8; i++) {
            left = valueRects[i * 4];
            top = valueRects[i * 4 + 1];
            right = valueRects[i * 4 + 2];
            bottom = valueRects[i * 4 + 3];
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);//?????????
            paint.setStrokeWidth(10); //????????????
            canvas.drawRect(left, top, right, bottom, paint);
        }
        for (int i = 0; i < 6; i++) {
            left = keywordRects[i * 4];
            top = keywordRects[i * 4 + 1];
            right = keywordRects[i * 4 + 2];
            bottom = keywordRects[i * 4 + 3];
            paint.setColor(Color.GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10);
            canvas.drawRect(left, top, right, bottom, paint);
        }
        ivPicture.setImageBitmap(mutableBitmap);//img: ?????????xml????????????ImagView??????
    }


    private void showResult(List<GetResult.ResultBean> result) {
        bottomSheetDialog.setContentView(bottomView);
        bottomSheetDialog.getWindow();
        RecyclerView rvResult = bottomView.findViewById(R.id.rv_result);
        ResultAdapter adapter = new ResultAdapter(R.layout.item_result_rv, result);
        rvResult.setLayoutManager(new LinearLayoutManager(this));
        rvResult.setAdapter(adapter);
        //????????????
        pbLoading.setVisibility(View.GONE);
        //????????????
        bottomSheetDialog.show();
    }


    /**
     * ??????????????????
     *
     * @param token       token
     * @param imageBase64 ??????Base64
     * @param imgUrl      ??????Url
     */
    private void ImageRecognition(String token, String imageBase64, String imgUrl) {
        service.getRecognitionResult(token, imageBase64, imgUrl).enqueue(new NetCallBack<GetResult>() {
            @Override
            public void onSuccess(Call<GetResult> call, Response<GetResult> response) {
                List<GetResult.ResultBean> result = response.body() != null ? response.body().getResult() : null;
                if (result != null && result.size() > 0) {
                    //??????????????????
                    showRecognitionResult(result);
                } else {
                    pbLoading.setVisibility(View.GONE);
                    showMsg("??????????????????????????????");
                }
            }

            @Override
            public void onFailed(String errorStr) {
                pbLoading.setVisibility(View.GONE);
                Log.e(TAG, "????????????????????????????????????" + errorStr);
            }
        });
    }

    /**
     * ??????????????????
     *
     * @param view
     */
    @SuppressLint("CheckResult")
    public void IdentifyTakePhotoImage(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            rxPermissions.request(
                            Manifest.permission.CAMERA)
                    .subscribe(grant -> {
                        if (grant) {
                            //????????????
                            turnOnCamera();
                        } else {
                            showMsg("??????????????????");
                        }
                    });
        } else {
            turnOnCamera();
        }
    }

    /**
     * ????????????
     */
    private void turnOnCamera() {
        SimpleDateFormat timeStampFormat = new SimpleDateFormat("HH_mm_ss");
        String filename = timeStampFormat.format(new Date());
        //??????File??????
        outputImage = new File(getExternalCacheDir(), "takePhoto" + filename + ".jpg");
        Uri imageUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            imageUri = FileProvider.getUriForFile(this,
                    "com.example.imagerecognitiondemo.fileprovider", outputImage);
        } else {
            imageUri = Uri.fromFile(outputImage);
        }
        //????????????
        Intent intent = new Intent();
        intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, TAKE_PHOTO_CODE);
    }

    /**
     * ??????????????????
     */
    public void IdentifyWebPictures(View view) {
        pbLoading.setVisibility(View.VISIBLE);
        if (accessToken == null) {
            showMsg("??????AccessToken???null");
            return;
        }
        String imgUrl = "https://bkimg.cdn.bcebos.com/pic/4610b912c8fcc3ce270e272c9945d688d53f20e7?x-bce-process=image/watermark,image_d2F0ZXIvYmFpa2U5Mg==,g_7,xp_5,yp_5";
        //????????????
        Glide.with(this).load(imgUrl).into(ivPicture);
        showMsg("???????????????");
        service.getRecognitionResult(accessToken, null, imgUrl).enqueue(new NetCallBack<GetResult>() {
            @Override
            public void onSuccess(Call<GetResult> call, Response<GetResult> response) {
                List<GetResult.ResultBean> result = response.body() != null ? response.body().getResult() : null;
                if (result != null && result.size() > 0) {
                    //??????????????????
                    showRecognitionResult(result);
                } else {
                    pbLoading.setVisibility(View.GONE);
                    showMsg("??????????????????????????????");
                }
            }
            @Override
            public void onFailed(String errorStr) {
                pbLoading.setVisibility(View.GONE);
                Log.e(TAG, "????????????????????????????????????" + errorStr);
            }
        });
    }

    /**
     * ??????????????????
     *
     */
    @SuppressLint("CheckResult")
    public void IdentifyAlbumPictures(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            rxPermissions.request(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .subscribe(grant -> {
                        if (grant) {
                            //????????????
                            openAlbum();
                        } else {
                            showMsg("??????????????????");
                        }
                    });
        } else {
            openAlbum();
        }
    }

    /**
     * ????????????
     */
    private void openAlbum() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, OPEN_ALBUM_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            pbLoading.setVisibility(View.VISIBLE);
            if (requestCode == OPEN_ALBUM_CODE) {
                //??????????????????
                String[] filePathColumns = {MediaStore.Images.Media.DATA};
                final Uri imageUri = Objects.requireNonNull(data).getData();
                Cursor cursor = getContentResolver().query(imageUri, filePathColumns, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumns[0]);
                //??????????????????
                String imagePath = cursor.getString(columnIndex);
                cursor.close();
                //??????
                localImageRecognition(imagePath);
            } else if(requestCode == TAKE_PHOTO_CODE) {
                String imagePath = outputImage.getAbsolutePath();
                localImageRecognition(imagePath);
            }
        } else {
            showMsg("???????????????");
        }
    }

    /**
     * ??????????????????
     */
    private void localImageRecognition(String imagePath) {
        try {
            if (accessToken == null) {
                showMsg("??????AccessToken???null");
                return;
            }
            //??????????????????????????????
            Glide.with(this).load(imagePath).into(ivPicture);
            //?????????????????????
            byte[] imgData = FileUtil.readFileByBytes(imagePath);
            //?????????Base64
            String imageBase64 = Base64Util.encode(imgData);
            //????????????
            ImageRecognition(accessToken, imageBase64, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ???????????????????????????
     *
     */
    private void showRecognitionResult(List<GetResult.ResultBean> result) {
        bottomSheetDialog.setContentView(bottomView);
        bottomSheetDialog.getWindow();
        RecyclerView rvResult = bottomView.findViewById(R.id.rv_result);
        ResultAdapter adapter = new ResultAdapter(R.layout.item_result_rv, result);
        rvResult.setLayoutManager(new LinearLayoutManager(this));
        rvResult.setAdapter(adapter);
        //????????????
        pbLoading.setVisibility(View.GONE);
        //????????????
        bottomSheetDialog.show();

    }

    /**
     * Toast??????
     */
    private void showMsg(String msg){
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();
    }

    /**
     * ??????API????????????
     */
    private void requestApiGetToken() {
        String grantType = "client_credentials";
        String apiKey = "ChPW1tTtOYxAbzxKV6LsOfzZ";
        String apiSecret = "UMwVqNX5uVoX2zlylqZl0mMjuttYwaHF";
        service.getToken(grantType, apiKey, apiSecret)
                .enqueue(new NetCallBack<GetToken>() {
                    @Override
                    public void onSuccess(Call<GetToken> call, Response<GetToken> response) {
                        if (response.body() != null) {
                            //??????Token
                            accessToken = response.body().getAccess_token();
                            Log.d(TAG,accessToken);
                        }
                    }

                    @Override
                    public void onFailed(String errorStr) {
                        Log.e(TAG, "??????Token????????????????????????" + errorStr);
                        accessToken = null;
                    }
                });
    }

    public void onSuccess(Call<GetToken> call, Response<GetToken> response) {
        if (response.body() != null) {
            //??????Token
            accessToken = response.body().getAccess_token();
            //???????????? ???
            long expiresIn = response.body().getExpires_in();
            //???????????? ???
            long currentTimeMillis = System.currentTimeMillis() / 1000;
            //????????????
            SPUtils.putString(Constant.TOKEN, accessToken, MainActivity.this);
            SPUtils.putLong(Constant.GET_TOKEN_TIME, currentTimeMillis, MainActivity.this);
            SPUtils.putLong(Constant.TOKEN_VALID_PERIOD, expiresIn, MainActivity.this);
        }
    }

    /**
     * Token????????????
     *
     * @return
     */
    private boolean isTokenExpired() {
        //??????Token?????????
        long getTokenTime = SPUtils.getLong(Constant.GET_TOKEN_TIME, 0, this);
        //??????Token???????????????
        long effectiveTime = SPUtils.getLong(Constant.TOKEN_VALID_PERIOD, 0, this);
        //????????????????????????
        long currentTime = System.currentTimeMillis() / 1000;

        return (currentTime - getTokenTime) >= effectiveTime;
    }

    /**
     * ????????????Token
     */
    private String getAccessToken() {
        String token = SPUtils.getString(Constant.TOKEN, null, this);
        if (token == null) {
            //??????API????????????
            requestApiGetToken();
        } else {
            //?????????Token????????????
            if (isTokenExpired()) {
                //??????
                requestApiGetToken();
            } else {
                accessToken = token;
            }
        }
        return accessToken;
    }


}