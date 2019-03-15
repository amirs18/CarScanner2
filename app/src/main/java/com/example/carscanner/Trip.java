package com.example.carscanner;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.util.Date;

public class Trip extends AppCompatActivity {
    private Date date;
    private int id;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip);
    }
}
