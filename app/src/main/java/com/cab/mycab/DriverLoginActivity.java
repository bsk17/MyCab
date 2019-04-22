package com.cab.mycab;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DriverLoginActivity extends AppCompatActivity {

    private EditText mEmail, mPassword;
    private Button mLogin, mRegister;

    // The entry point of the Firebase Authentication SDK
    private FirebaseAuth mAuth;
    // Listener called when there is a change in the authentication state.
    private FirebaseAuth.AuthStateListener firebaseAuthListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_login);

        // to obtain the instance of the FirebaseAuth class
        mAuth = FirebaseAuth.getInstance();

        // this is to obtain the instance of AuthListener
        firebaseAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {

                // user will save the information of the current user logged in
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                // now we will check for the user and continue to other activity
                if(user != null){
                    Intent intent = new Intent(DriverLoginActivity.this,
                            DriverMapActivity.class);
                    startActivity(intent);
                    finish();
                    return;
                }
            }
        };

        // the two EditText which receives the value
        mEmail = findViewById(R.id.driver_email);
        mPassword = findViewById(R.id.driver_password);

        // the two button which again performs the functions
        mLogin = findViewById(R.id.driver_login);
        mRegister = findViewById(R.id.driver_register);

        // this will decide what to do when user registers
        mRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = mEmail.getText().toString();
                final String password = mPassword.getText().toString();

                // now we use the firebase authentication function to register the user to the DB
                // since we have used the Email & Password method in firebase console
                mAuth.createUserWithEmailAndPassword(email, password).
                        addOnCompleteListener(DriverLoginActivity.this,
                                new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {

                                // now we check for whether the process was successful or not
                                if (!task.isSuccessful()){
                                    Toast.makeText(DriverLoginActivity.this,
                                            "Oops!! Something went wrong",
                                            Toast.LENGTH_SHORT).show();
                                }else{

                                    // when successful then we add the data to the
                                    // "Riders" table in the DB we created in firebase
                                    String user_id = mAuth.getCurrentUser().getUid();

                                    // DBreference is going to point to the "Riders" table
                                    DatabaseReference current_user_db = FirebaseDatabase
                                            .getInstance().getReference()
                                            .child("Users")
                                            .child("Riders")
                                            .child(user_id)
                                            .child("name");
                                    current_user_db.setValue(email);
                                }
                            }
                        });
            }
        });

        // to set the function of the login button
        mLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = mEmail.getText().toString();
                final String password = mPassword.getText().toString();

                // now we use the firebase authentication function to Sign in the user
                // since we have used the Email & Password method in firebase console
                mAuth.signInWithEmailAndPassword(email, password).
                        addOnCompleteListener(DriverLoginActivity.this,
                                new OnCompleteListener<AuthResult>() {
                                    @Override
                                    public void onComplete(@NonNull Task<AuthResult> task) {

                                        // now we check for whether the process was successful or not
                                        if (!task.isSuccessful()){
                                            Toast.makeText(DriverLoginActivity.this,
                                                    "Oops!! Something went wrong",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                        // we don't have to write the else part because it
                                        // automatically calls the onAuthStateChanged() which we
                                        // have declared above
                                    }
                                });
            }
        });
    }

    // when we start the activity we have to start the listener also
    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(firebaseAuthListener);
    }

    // // when we stop the activity we have to remove the listener
    @Override
    protected void onStop() {
        super.onStop();
        mAuth.removeAuthStateListener(firebaseAuthListener);
    }
}
