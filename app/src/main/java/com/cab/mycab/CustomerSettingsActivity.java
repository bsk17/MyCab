// In this activity we will focus on saving the customer details in the database using the same
// concepts as in the previous activities.
// provide storage access to the app

package com.cab.mycab;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CustomerSettingsActivity extends AppCompatActivity {

    private EditText mNameField, mPhoneField;

    private Button mBack, mConfirm;

    private ImageView mProfileImage;

    private FirebaseAuth mAuth;
    private DatabaseReference mCustomerDatabase;

    private String userId;
    private String mName;
    private String mPhone;
    private String mProfileImageUrl;

    private Uri resultUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_settings);

        mProfileImage = findViewById(R.id.profileImage);

        mNameField = findViewById(R.id.name);
        mPhoneField = findViewById(R.id.phone);

        mBack = findViewById(R.id.back);
        mConfirm = findViewById(R.id.confirm);

        mAuth = FirebaseAuth.getInstance();
        userId = mAuth.getCurrentUser().getUid();

        // this is the object to handle database
        mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users")
                .child("Customers").child(userId);

        getUserInfo();

        // when we click the image then we should move on to "GALLERY" to select image
        mProfileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // as we have to pick an image from the gallery
                Intent intent = new Intent(Intent.ACTION_PICK);
                // we set the type to restrict the selection
                intent.setType("image/*");
                // we can call many intents so we maintain a request code to differentiate intents
                // we jump to onActivityResult() from here
                startActivityForResult(intent, 1);
                // then we save the information
            }
        });

        mConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserInformation();
            }
        });

        mBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                return;
            }
        });
    }

    // we are using this function so that once we have saved the data then the fields are
    // automatically filled with previous data
    private void getUserInfo(){
        mCustomerDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("name")!=null){
                        mName = map.get("name").toString();
                        mNameField.setText(mName);
                    }
                    if (map.get("phone")!=null){
                        mPhone = map.get("phone").toString();
                        mPhoneField.setText(mPhone);
                    }
                    if (map.get("profileImageUrl")!=null){
                        mProfileImageUrl = map.get("profileImageUrl").toString();
                        // using Glide we move the image using url into the ImageView
                        Glide.with(getApplicationContext()).load(mProfileImageUrl)
                                .into(mProfileImage);
                    }
                }
            }

            // we wont use this for now
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    // this function we get called when we press the confirm button and data will be saved in the
    // database
    private void saveUserInformation() {
        mName = mNameField.getText().toString();
        mPhone = mPhoneField.getText().toString();

        Map userInfo = new HashMap();
        userInfo.put("name", mName);
        userInfo.put("phone", mPhone);
        mCustomerDatabase.updateChildren(userInfo);

        // this the image saving part in database storage
        if (resultUri != null){
            // Storage reference works the same as database reference
            // filepath is the object to handle storage
            final StorageReference filePath = FirebaseStorage.getInstance().getReference()
                    .child("profile_images").child(userId);
            // Bitmap is used to handle images
            Bitmap bitmap = null;

            try {
                bitmap = MediaStore.Images.Media
                        .getBitmap(getApplicationContext().getContentResolver(), resultUri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // we compress the image in JPEg format to save the space in limited storage of FireBase
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // quality varies from 0-100
            bitmap.compress(Bitmap.CompressFormat.JPEG, 10, baos);
            byte[] data = baos.toByteArray();
            UploadTask uploadTask = filePath.putBytes(data);

            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    finish();
                    return;
                }
            });

            // we also put the image url in the database
            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                     Task<Uri> downloadUrl = filePath.getDownloadUrl();
                     // we put the url into the database
                     Map newImage = new HashMap();
                     newImage.put("profileImageUrl", downloadUrl.toString());
                     mCustomerDatabase.updateChildren(newImage);
                     finish();
                     return;
                }
            });
        }
        else {
            finish();
        }
    }

    // when we get the image what we should do
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == Activity.RESULT_OK){
            // first we get the data
            final Uri imageUri = data.getData();
            // we store the data(image) in a variable
            resultUri = imageUri;
            // we set the imageView to the image we received just above
            mProfileImage.setImageURI(resultUri);
        }
    }
}
