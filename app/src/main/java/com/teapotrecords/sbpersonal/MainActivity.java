package com.teapotrecords.sbpersonal;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private HttpServer server;
    private static final String COMMAND_TAG = "command";
    private static final String LYRICS_TAG = "lyrics";
    private static final String INFO_TAG = "info";
    private static final String VERSION = "A0.1";
    private Typeface fontCalibri = null;
    private String serverPort = "8080";
    public static final String PREFS_NAME = "songbase_personal";


    private static final String DefaultInfo = "<center><big>Songbase Personal Viewer<br/>for Android<br/>(C) 2022 Teapot Records</big></center>";

    void receiveMessage(HashMap<String, String> hash) {
        String command = hash.get(COMMAND_TAG);
        if (command == null) return;
        String b64code = hash.get(LYRICS_TAG);
        if (b64code != null) {
            final String html = new String(Base64.decode(b64code, Base64.DEFAULT)).
                    replaceAll("\n", "<br/>");
            if (command.equals(LYRICS_TAG)) {
                this.runOnUiThread(() -> {
                    TextView tv = findViewById(R.id.text_view);
                    tv.setText(Html.fromHtml("<center>"+html+"</center>"));
                });
            }
        }
    }

    class LyricHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            HashMap<String,String> key_values = new HashMap<>();
            if (t.getRequestMethod().equals("POST")) {

                String query = "";
                try (InputStreamReader in = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8)) {
                    BufferedReader br = new BufferedReader(in);
                    query = br.readLine();
                    if (query != null) {
                        query = URLDecoder.decode(query, "UTF-8");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

                assert query != null;
                String[] bits = query.split("&");
                for (String bit : bits)
                    key_values.put(bit.substring(0, bit.indexOf("=")), bit.substring(bit.indexOf("=") + 1));

            }
            String response = "OK";
            String command = key_values.get(COMMAND_TAG);
            if (command!=null) {
                if (command.equals(INFO_TAG)) {
                    response = VERSION;
                }
            }

            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
            receiveMessage(key_values);
        }
    }

    private void startServer() {
        new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                try {
                    server = HttpServer.create(new InetSocketAddress(Integer.parseInt(serverPort)), 0);
                    server.createContext("/", new LyricHandler());
                    server.setExecutor(null);
                    server.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute("");

    }

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        TextView tv = findViewById(R.id.text_view);
        if (fontCalibri == null) fontCalibri = Typeface.createFromAsset(getAssets(), "calibri.ttf");
        tv.setTypeface(fontCalibri);
        tv.setTextSize(42);
        tv.setText(Html.fromHtml(DefaultInfo));

        // Load preferences

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        serverPort = settings.getString("port", "8080");
        startServer();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        if (item.getItemId() == R.id.server_port) {
            String previousPort = serverPort;
            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
            alert.setTitle("Network Settings"); //Set Alert dialog title here
            Context context = getApplicationContext();
            WifiManager wifiMan = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInf = wifiMan.getConnectionInfo();
            int ipAddress = wifiInf.getIpAddress();
            @SuppressLint("DefaultLocale") String ip = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
            alert.setMessage("IP " + ip + ", Port:"); //Message here
            final EditText input = new EditText(MainActivity.this);
            input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
            input.setText(serverPort);
            alert.setView(input);
            // End of onClick(DialogInterface dialog, int whichButton)
            alert.setPositiveButton("OK", (dialog, whichButton) -> {
                serverPort = input.getEditableText().toString();
                try {
                    Integer.parseInt(serverPort);
                } catch (Exception e) {
                    serverPort = "8080";
                }
                if (!serverPort.equals(previousPort)) {
                    server.stop(0);
                    startServer();
                }

            }); //End of alert.setPositiveButton

            alert.setNegativeButton("CANCEL", (dialog, whichButton) -> dialog.cancel()); //End of alert.setNegativeButton
            AlertDialog alertDialog = alert.create();
            alertDialog.show();
            return true;
        }

        if (item.getItemId() == R.id.about_menu) {
            AlertDialog.Builder ver = new AlertDialog.Builder(MainActivity.this);
            ver.setTitle("Songbase Personal Viewer");
            ver.setMessage("Version: " + VERSION);
            ver.setCancelable(false);
            ver.setPositiveButton("OK", (dialog, whichButton) -> dialog.cancel());
            AlertDialog ad = ver.create();
            ad.show();
            return true;
        } else if (item.getItemId() == R.id.exit_menu) {
            finish();
            System.exit(0);
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    // Save preferences on stopping
    @Override
    protected void onStop() {
        super.onStop();
        // We need an Editor object to make preference changes.
        // All objects are from android.context.Context
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("port", serverPort);
        // Commit the edits!
        editor.apply();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

}