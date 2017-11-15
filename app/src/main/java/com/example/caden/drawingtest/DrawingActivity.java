package com.example.caden.drawingtest;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class DrawingActivity extends AppCompatActivity implements View.OnTouchListener {

    final int dp56 = dpToPx(56);

    // views
    private DrawModel drawModel;
    private DrawView drawView;
    private PointF mTmpPoint = new PointF();

    private float mLastX;
    private float mLastY;

    private ImageManager im;
    private int brushColor;

    //    FireBase
    private FirebaseStorage mStorage;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private ProgressBar barSend;
    private FloatingActionButton fabSend;

    ConstraintLayout clDrawMain;
    ConstraintSet constraintSet = new ConstraintSet();

    private static final int PIXEL_WIDTH = 280;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);

        //get drawing view from XML (where the finger writes the number)
        drawView = findViewById(R.id.draw);
        //get the model object
        drawModel = new DrawModel(PIXEL_WIDTH, PIXEL_WIDTH);
        clDrawMain = findViewById(R.id.cl_draw_main);
        constraintSet.clone(clDrawMain);

        //init the view with the model object
        drawView.setModel(drawModel);
        // give it a touch listener to activate when the user taps
        drawView.setOnTouchListener(this);

        im = new ImageManager();
        brushColor = getIntent().getIntExtra("color", 0);

        fabSend = findViewById(R.id.fab_send);
        barSend = findViewById(R.id.pbar_send);

        mStorage = FirebaseStorage.getInstance();
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    //OnResume() is called when the user resumes his Activity which he left a while ago,
    // //say he presses home button and then comes back to app, onResume() is called.
    protected void onResume() {
        drawView.onResume();
        super.onResume();
    }

    @Override
    //OnPause() is called when the user receives an event like a call or a text message,
    // //when onPause() is called the Activity may be partially or completely hidden.
    protected void onPause() {
        drawView.onPause();
        super.onPause();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    //this method detects which direction a user is moving
    //their finger and draws a line accordingly in that
    //direction
    public boolean onTouch(View v, MotionEvent event) {
        //get the action and store it as an int
        int action = event.getAction() & MotionEvent.ACTION_MASK;
        //actions have predefined ints, lets match
        //to detect, if the user has touched, which direction the users finger is
        //moving, and if they've stopped moving

        //if touched
        if (action == MotionEvent.ACTION_DOWN) {
            //begin drawing line
            processTouchDown(event);
            return true;
            //draw line in every direction the user moves
        } else if (action == MotionEvent.ACTION_MOVE) {
            processTouchMove(event);
            return true;
            //if finger is lifted, stop drawing
        } else if (action == MotionEvent.ACTION_UP) {
            processTouchUp();
            return true;
        }
        return false;
    }

    //draw line down

    private void processTouchDown(MotionEvent event) {
        //calculate the x, y coordinates where the user has touched
        mLastX = event.getX();
        mLastY = event.getY();
        //user them to calculate the position
        drawView.calcPos(mLastX, mLastY, mTmpPoint);
        //store them in memory to draw a line between the
        //difference in positions
        float lastConvX = mTmpPoint.x;
        float lastConvY = mTmpPoint.y;
        //and begin the line drawing
        drawModel.startLine(lastConvX, lastConvY);
    }

    //the main drawing function
    //it actually stores all the drawing positions
    //into the drawModel object
    //we actually render the drawing from that object
    //in the drawRenderer class
    private void processTouchMove(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        drawView.calcPos(x, y, mTmpPoint);
        float newConvX = mTmpPoint.x;
        float newConvY = mTmpPoint.y;
        drawModel.addLineElem(newConvX, newConvY);

        mLastX = x;
        mLastY = y;
        drawView.invalidate();
    }

    private void processTouchUp() {
        drawModel.endLine();
    }

    public void clear(View v) {
        drawModel.clear();
        drawView.reset();
        drawView.invalidate();
    }

    public void sendImage(View v) {

        int height = findViewById(R.id.cv_drawview).getHeight();

        TransitionManager.beginDelayedTransition(clDrawMain);
        constraintSet.constrainHeight(R.id.fab_send, 0);
        constraintSet.constrainWidth(R.id.fab_send, 0);
        constraintSet.constrainWidth(R.id.pbar_send, dp56);
        constraintSet.constrainHeight(R.id.pbar_send, dp56);

        constraintSet.constrainHeight(R.id.cv_drawview, 0);

        constraintSet.applyTo(clDrawMain);




        Bitmap bmp = drawView.getBitmapData();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);

        UUID uuid = UUID.randomUUID();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd EEE");
        DateFormat timeFormat = new SimpleDateFormat("hh:mm:ss aaa");
        DateFormat uploadFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
        String imageFileName = im.imgFileName;
        FirebaseUser u = mAuth.getCurrentUser();

        /* Upload Drawing */
        String path = "uploaded/" + imageFileName + "/" + uploadFormat.format(date) + "/" + uuid + ".jpg";
        StorageReference mStorageRef = mStorage.getReference(path);

        StorageMetadata metadata = new StorageMetadata.Builder()
                .setCustomMetadata("text", "my first upload")
                .build();
        UploadTask upTask = mStorageRef.putBytes(baos.toByteArray(), metadata);
        upTask.addOnSuccessListener(this, taskSnapshot -> {
            TransitionManager.beginDelayedTransition(clDrawMain);
            constraintSet.constrainWidth(R.id.pbar_send, 0);
            constraintSet.constrainHeight(R.id.pbar_send, 0);
            constraintSet.constrainHeight(R.id.fab_send, dp56);
            constraintSet.constrainWidth(R.id.fab_send, dp56);
            constraintSet.constrainHeight(R.id.cv_drawview, height);
            constraintSet.applyTo(clDrawMain);

        });

        /* Update Database Reference */
        mDatabase.child(imageFileName).child(dateFormat.format(date)).child(timeFormat.format(date)).child("image_name").setValue(uuid.toString());
        mDatabase.child(imageFileName).child(dateFormat.format(date)).child(timeFormat.format(date)).child("username").setValue(u.getEmail());
        mDatabase.child(imageFileName).child(dateFormat.format(date)).child(timeFormat.format(date)).child("user_uid").setValue(u.getUid());
    }

    /**
     * Converts dp into pixel values
     * @param dp    display pixels
     * @return      pixel values
     */
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dp, getResources().getDisplayMetrics());
    }
}
