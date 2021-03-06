package com.example.frontend.ui.clothes;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;
import com.example.frontend.DotSpinner;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.test.espresso.idling.CountingIdlingResource;

import com.example.frontend.MainActivity;
import com.example.frontend.R;
import com.example.frontend.ServerCommAsync;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static android.widget.Toast.makeText;

public class ClothesFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    private static final String TAG ="ClothesFragment" ;
    private static final String EMPTY_STRING = "";
    private final int ADD = 1;
    private final int EDIT = 2;

    private String userToken, userId;

    private ImageView buttonAdd, allImage, shirtsImage, trousersImage, shoesImage;
    private GridLayout clothesLayout;

    private int selectedId;
    private String message = EMPTY_STRING;
    private static List<String> clothesIdList = new ArrayList<>();
    private static HashMap<Integer, String> clothesIdMap = new HashMap<>();
    private static HashMap<String, String> categoryMap = new HashMap<>();

    private static String selectedCategory = EMPTY_STRING;

    static View root;
    static CountingIdlingResource idlingResource = new CountingIdlingResource("send_add_clothes_request");

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_clothes, container, false);
        userToken = MainActivity.getUser().getUserToken();
        userId = MainActivity.getUser().getUserId();

        buttonAdd = root.findViewById(R.id.btn_clothes_add);
        buttonAdd.setOnClickListener(this);
//        allImage = root.findViewById(R.id.iv_all);
        allImage = root.findViewById(R.id.iv_outerwear);
        allImage.setOnClickListener(this);
        shirtsImage = root.findViewById(R.id.iv_shirts);
        shirtsImage.setOnClickListener(this);
        trousersImage = root.findViewById(R.id.iv_trousers);
        trousersImage.setOnClickListener(this);
        shoesImage = root.findViewById(R.id.iv_shoes);
        shoesImage.setOnClickListener(this);
        clothesLayout = root.findViewById(R.id.gl_clothes);

        if (clothesIdList.size() == 0) {
            getAllClothesFromServer();
        }
        addAllClothesToCloset();
        selectedCategory = "all";

        return root;
    }

    public void setAdapter(int textArrayResId, DotSpinner spinner) {
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(ClothesFragment.this.getContext(),
                textArrayResId, android.R.layout.simple_spinner_item);

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_clothes_add:
                idlingResource.increment();
                Intent addClothesIntent = new Intent(ClothesFragment.this.getContext(), AddClothesActivity.class);
                startActivityForResult(addClothesIntent, ADD);
                idlingResource.decrement();
                break;

            case R.id.iv_outerwear:
                clothesLayout.removeAllViews();
                addClothesByCategoryFromServer("outerwear");
                selectedCategory = "outerwear";
                break;

            case R.id.iv_shirts:
                clothesLayout.removeAllViews();
                addClothesByCategoryFromServer("shirts");
                selectedCategory = "shirts";
                break;

            case R.id.iv_trousers:
                clothesLayout.removeAllViews();
                addClothesByCategoryFromServer("trousers");
                selectedCategory = "trousers";
                break;

            case R.id.iv_shoes:
                clothesLayout.removeAllViews();
                addClothesByCategoryFromServer("shoes");
                selectedCategory = "shoes";
                break;

            default:
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        selectedId = parent.getId();

        if (parent.getSelectedItem().toString().equals("Edit")) {
            String clothesId = clothesIdMap.get(selectedId);
            Intent editClothesIntent = new Intent(ClothesFragment.this.getContext(), EditClothesActivity.class);
            editClothesIntent.putExtra("clothesId", clothesId);
            startActivityForResult(editClothesIntent, EDIT);
        }

        else if (parent.getSelectedItem().toString().equals("Delete")) {
            String clothesId = clothesIdMap.get(selectedId);
            deleteImageFromServer(clothesId);
            deleteClothDataFromServer(clothesId);
            deleteClothesFromCloset(selectedId);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && requestCode == ADD) {
            // here you can retrieve your bundle data.
            String clothesId = data.getStringExtra("clothesId");
            String category = data.getStringExtra("category");

            clothesIdList.add(clothesId);
            if (category.equals("outerwear")) {
                categoryMap.put(clothesId, "outerwear");
            }
            else if (category.equals("shirts")) {
                categoryMap.put(clothesId, "shirts");
            }
            else if (category.equals("trousers")) {
                categoryMap.put(clothesId, "trousers");
            }
            else if (category.equals("shoes")){
                categoryMap.put(clothesId, "shoes");
            }

            if (category.equals(selectedCategory) || selectedCategory.equals("all")) {
                addClothesToCloset(clothesId);
            }
        }

        else if (resultCode == Activity.RESULT_OK && requestCode == EDIT) {
            editClothesInCloset(selectedId);
        }
    }

    private void editClothesInCloset(int selectedId) {
        ImageView image = root.findViewById(selectedId - 1);
        String clothesId = clothesIdMap.get(selectedId);
        Bitmap bitmap = getClothesImage(clothesId);
        image.setImageBitmap(bitmap);
    }

    private void deleteImageFromServer(String clothesId) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("http://closet-cpen321.westus.cloudapp.azure.com/api/images/" + userId + "/" + clothesId)
                .addHeader("Authorization","Bearer "+ userToken)
                .delete()
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String responseStr = Objects.requireNonNull(response.body().string());
                Log.d(TAG, "Successfully delete image from server: " + responseStr);
            }
        });
    }

    private void deleteClothDataFromServer(String clothesId) {
        ServerCommAsync serverCommunication = new ServerCommAsync();

        Log.d(TAG,"prepared to deleteClothDataFromServer");
        serverCommunication.deleteWithAuthentication("http://closet-cpen321.westus.cloudapp.azure.com/api/clothes/"+userId+ "/" + clothesId, userToken, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                Log.d(TAG,"Fail to send request to server");
                Log.d(TAG, String.valueOf(e));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {

                String responseStr = Objects.requireNonNull(response.body()).string();
                Log.d(TAG,responseStr);

                JSONObject responseJson = null;
                try {
                    responseJson = new JSONObject(responseStr);
                    if(responseJson.has("message"))
                        message = responseJson.getString("message");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (response.isSuccessful()) {
                    //make a toast to let the server's message display to the user
                    if(Objects.requireNonNull(responseJson).has("message") ){
                        getActivity().runOnUiThread(new Runnable() {
                            public void run() {
                                final Toast toast = makeText(getContext(),message,Toast.LENGTH_LONG);
                                toast.show();
                            }
                        });
                    }

                } else {
                    // Request not successful
                    if(Objects.requireNonNull(responseJson).has("message") ){
                        getActivity().runOnUiThread(new Runnable() {
                            public void run() {
                                final Toast toast = makeText(getContext(),message,Toast.LENGTH_LONG);
                                toast.show();
                            }
                        });
                    }
                }
                Log.d(TAG,"finish deleteClothDataFromServer");
            }
        });
    }

    private void deleteClothesFromCloset(int selectedId) {
        ConstraintLayout clothes = root.findViewById(selectedId + 1);
        String clothesId = clothesIdMap.get(selectedId);
        clothesLayout.removeView(clothes);
        clothesIdList.remove(clothesId);
        clothesIdMap.remove(selectedId);
    }

    private void getAllClothesFromServer() {
        ServerCommAsync serverComm = new ServerCommAsync();

        serverComm.getWithAuthentication("http://closet-cpen321.westus.cloudapp.azure.com/api/clothes/" + userId, userToken, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String responseStr = Objects.requireNonNull(response.body().string());
                if (response.isSuccessful()) {
                    JSONObject responseJSON;
                    try {
                        responseJSON = new JSONObject(responseStr);
                        extractResponseClothesData(responseJSON);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void extractResponseClothesData(JSONObject responseJSON) throws JSONException {
        JSONArray clothesArray = responseJSON.getJSONArray("clothes");
        for (int i = 0; i < clothesArray.length(); i++) {
            JSONObject clothes = clothesArray.getJSONObject(i);
            String clothesId = EMPTY_STRING;
            if (clothes.has("id")) {
                clothesId = clothes.getString("id");
                clothesIdList.add(clothesId);
            }
            if (clothes.getString("category").equals("outerwear")) {
                categoryMap.put(clothesId, "outerwear");
            }
            if (clothes.getString("category").equals("shirts")) {
                categoryMap.put(clothesId, "shirts");
            }
            else if (clothes.getString("category").equals("trousers")) {
                categoryMap.put(clothesId, "trousers");
            }
            else if (clothes.getString("category").equals("shoes")){
                categoryMap.put(clothesId, "shoes");
            }
        }
        addAllClothesToCloset();
    }

    private void addClothesToCloset(String clothesId) {
        ImageView image = new ImageView(getContext());
        image.setId(View.generateViewId());
        ConstraintLayout.LayoutParams imageParams = new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        imageParams.width = 300;
        imageParams.height = 300;
        image.setLayoutParams(imageParams);
        Bitmap bitmap = getClothesImage(clothesId);
        image.setImageBitmap(bitmap);

        DotSpinner spinner = new DotSpinner(getContext());
        spinner.setId(View.generateViewId());
        ConstraintLayout.LayoutParams spinnerParams = new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerParams.width = 90;
        spinnerParams.height = 90;
        spinner.setLayoutParams(spinnerParams);
        spinner.setBackgroundResource(R.drawable.dots);
        setAdapter(R.array.edit_delete_array, spinner);
        spinner.setOnItemSelectedListener(this);

        final ConstraintLayout clothes = new ConstraintLayout(getContext());
        clothes.setId(View.generateViewId());
        ConstraintLayout.LayoutParams clothesParams = new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        clothesParams.width = 300;
        clothesParams.height = 300;
        clothesParams.leftMargin = 45;
        clothesParams.bottomMargin = 45;
        clothes.setLayoutParams(clothesParams);
        clothes.addView(image);
        clothes.addView(spinner);
        ConstraintSet constraint = new ConstraintSet();
        constraint.clone(clothes);
        constraint.connect(spinner.getId(), ConstraintSet.RIGHT, image.getId(), ConstraintSet.RIGHT);
        constraint.applyTo(clothes);

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                clothesLayout.addView(clothes);
            }
        });

        clothesIdMap.put(spinner.getId(), clothesId);
    }

    private void addAllClothesToCloset() {
        for (int i = 0; i < clothesIdList.size(); i++) {
            String clothesId = clothesIdList.get(i);
            addClothesToCloset(clothesId);
        }
    }

    private void addClothesByCategoryFromServer(String category) {
        if (category.equals("outerwear")) {
            for (int i = 0; i < clothesIdList.size(); i++) {
                String clothesId = clothesIdList.get(i);
                if (categoryMap.get(clothesId).equals("outerwear")) {
                    addClothesToCloset(clothesId);
                }
            }
        }
        if (category.equals("shirts")) {
            for (int i = 0; i < clothesIdList.size(); i++) {
                String clothesId = clothesIdList.get(i);
                if (categoryMap.get(clothesId).equals("shirts")) {
                    addClothesToCloset(clothesId);
                }
            }
        }
        else if (category.equals("trousers")) {
            for (int i = 0; i < clothesIdList.size(); i++) {
                String clothesId = clothesIdList.get(i);
                if (categoryMap.get(clothesId).equals("trousers")) {
                    addClothesToCloset(clothesId);
                }
            }
        }
        else if (category.equals("shoes")){
            for (int i = 0; i < clothesIdList.size(); i++) {
                String clothesId = clothesIdList.get(i);
                if (categoryMap.get(clothesId).equals("shoes")) {
                    addClothesToCloset(clothesId);
                }
            }
        }
    }

    private Bitmap getClothesImage(String clothesId) {
        URL url;
        InputStream stream;
        BufferedInputStream buffer;

        try {
            url = new URL("http://closet-cpen321.westus.cloudapp.azure.com/UserClothingImages/"+userId+ "/" + clothesId + ".jpg");
            stream = url.openStream();
            buffer = new BufferedInputStream(stream);
            Bitmap bitmap = BitmapFactory.decodeStream(buffer);
            if (stream != null) {
                stream.close();
            }
            buffer.close();

            return bitmap;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d(TAG, "nothing is selected");
    }

    public static CountingIdlingResource getRegisterIdlingResourceInTest() {
        return idlingResource;
    }
}