package de.marci4.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Switch;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.lang.String;

import org.java_websocket.extensions.IExtension;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Collections;


public class MainActivity extends AppCompatActivity {
    // affichage du bouton sortie dans la bar d'action
    // il est defini dans res/menu/sortie
    Switch sConnection;
    Handler handler = new Handler();
    Runnable runnable;
    int delay = 5000;    //timer de reconnexion
    public TextView tEtatPortail;
    public TextView tEtatGarage;
    public TextView tTitrePortail;
    public TextView tTitreGarage;
    public int[][] etat;   // variable de décodage du message reçu du server
    public int flag_openvpn=0; //indique s'il y a eu une demande d'activation du tunnel pour ne pas le refaire

    // deux fonctions pour gerer le bouton d'arret de la barre de titre
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.sortie, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            //
            case R.id.action_cart:
                finish();
                break;
            default:
                break;
        }

        return true;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tEtatPortail = (TextView) findViewById(R.id.TEtatPortail);
        tEtatGarage = (TextView) findViewById(R.id.TEtatGarage);
        tTitrePortail = (TextView) findViewById(R.id.TPortail);
        tTitreGarage = (TextView) findViewById(R.id.TGarage);
        sConnection = findViewById(R.id.Switch_connection);
        sConnection.setChecked(false);
        sConnection.getThumbDrawable().setColorFilter(android.graphics.Color.RED, PorterDuff.Mode.MULTIPLY);
    }

    @Override
    protected void onResume() {
        // callback d'activation de l'application
        super.onResume();
        Log.i("onResume : ", "resume");
        ConnectToWebSocket();
        flag_openvpn=1;
        handler.postDelayed(runnable = new Runnable() {
            public void run() {
                handler.postDelayed(runnable, delay);
                if (Connected==0 && State==1 && ReConnect==0){
                    Log.i("websocket : ", "try to reconnected");
                    ReConnect = 1;
                    if (flag_openvpn == 0) {
                        Intent openVPN = new Intent ("net.openvpn.openvpn.CONNECT");
                        openVPN.setPackage ("net.openvpn.openvpn");
                        openVPN.setClassName ("net.openvpn.openvpn", "net.openvpn.unified.MainActivity");
                        openVPN.putExtra ("net.openvpn.openvpn.AUTOSTART_PROFILE_NAME", "PC Montgeron");
                        openVPN.putExtra ("net.openvpn.openvpn.AUTOCONNECT", true);
                        openVPN.putExtra ("net.openvpn.openvpn.APP_SECTION", "PC");
                        startActivity (openVPN);
                        flag_openvpn = 1;
                    }else {
                        ConnectToWebSocket ( );
                    }
                }

            }
        }, delay);
        State=1;
    }
    @Override
    protected void onPause() {
        // callback de mise en pause de l'application
        super.onPause();
        Log.i("onPause : ", "pause");
        if (Connected == 1) {
            mWebSocketClient.close ( );
        }State=0;
    }

    private WebSocketClient mWebSocketClient;
    // fonction associée au bouton d'ouverture et fermeture , envoi la chaine au serveur en fonction du tag du bouton
    public void action(android.view.View v){
        //callback lorsque l'on presse un bouton d'ordre
        // envoi de la chaine JSON contenu dans le tag du bouton
        Log.i("Commande : ", "put&Portes=" + (String) v.getTag());
        mWebSocketClient.send("put&Portes=" + (String) v.getTag());
    }

    static int Portail = 1;
    static int Garage = 0;
    static int Automate = 0;
    static int Etat = 1;
    static int Connected = 0;
    static int ReConnect = 0;
    static int State = 0;
    // function de connexion au serveur avec les callback associés
    private void ConnectToWebSocket() {
        URI uri;
        try {
            uri = new URI("ws://192.168.1.105:8887/publisher");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
        // preparation de la connexion our mettre le secondary protocol dans le header
        Draft_6455 draft_ocppOnly = new Draft_6455(Collections.<IExtension>emptyList(), Collections.<IProtocol>singletonList(new Protocol("Portes")));

        mWebSocketClient = new WebSocketClient(uri,draft_ocppOnly) {
            // etablissement de la communication avec le serveur
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                // callback de connexion
                // demande de l'etat des automates et position des portes
                Log.i("Websocket", "Opened");
                mWebSocketClient.send("get&Portes");
                Connected=1;
                // on execute la mise a jour de l'indicateur de connexion dans le contexte de UI principale
                runOnUiThread (new Runnable ( ) {
                    @Override
                    public void run() {
                        sConnection.setChecked (true);
                        sConnection.getThumbDrawable ( ).setColorFilter (android.graphics.Color.GREEN, PorterDuff.Mode.MULTIPLY);
                    }
                });
            }
            // reception d'un message du server
            @Override
            public void onMessage(String s) {
                final String message = s;
                Log.i("websocket",message + " " + s.length ());
                try {
                    //decodage du message recu
                    // affichage de l'etat de la porte par une couleur et un libellé
                    // mise en couleur de l'etat de l'automate de la porte
                    JSONArray mjsonArray = new JSONArray(message);
                    etat = new int[mjsonArray.length()][2];
                    for (int i = 0; i < mjsonArray.length(); i++) {
                        JSONArray jsa1 = mjsonArray.getJSONArray(i);
                        for (int j = 0; j < jsa1.length(); j++) {
                            etat[i][j] = jsa1.getInt(j);
                        }
                        // il faut etre dans le contexte du UI
                        runOnUiThread (new Runnable ( ) {
                            @Override
                            public void run() {
                                String[] PorteEtat = {"Fermé","Ouverture","Fermeture","Ouvert"};
                                String[] color = {"#00ff00","#ffff00","#ffff00","#ff0000"};             // vert jaune jaune rouge
                                tEtatPortail.setText(PorteEtat[etat[Portail][Etat]]);
                                tEtatPortail.setBackgroundColor(Color.parseColor(color[etat[Portail][Etat]]));
                                tEtatGarage.setText(PorteEtat[etat[Garage][Etat]]);
                                tEtatGarage.setBackgroundColor(Color.parseColor(color[etat[Garage][Etat]]));
                                if(etat[Portail][Automate]==0)tTitrePortail.setBackgroundColor(Color.parseColor("#ff0000"));
                                if(etat[Portail][Automate]==1)tTitrePortail.setBackgroundColor(Color.parseColor("#00ff00"));
                                if(etat[Garage][Automate]==0)tTitreGarage.setBackgroundColor(Color.parseColor("#ff0000"));
                                if(etat[Garage][Automate]==1)tTitreGarage.setBackgroundColor(Color.parseColor("#00ff00"));
                            }
                        });
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            // reception d'une fermeture de la communication avec le serveur
            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("Websocket", "Close " + s);
                Connected=0;
                ReConnect=0;
                runOnUiThread (new Runnable ( ) {
                    @Override
                    public void run() {
                        sConnection.setChecked (false);
                        sConnection.getThumbDrawable ( ).setColorFilter (android.graphics.Color.RED, PorterDuff.Mode.MULTIPLY);
                        tEtatPortail.setBackgroundColor(Color.parseColor("#FFFFFF"));
                        tEtatPortail.setText("inconnu");
                        tEtatGarage.setBackgroundColor(Color.parseColor("#FFFFFF"));
                        tEtatGarage.setText("inconnu");
                        tTitrePortail.setBackgroundColor(Color.parseColor("#ff0000"));
                        tTitreGarage.setBackgroundColor(Color.parseColor("#ff0000"));
                    }
                });
                mWebSocketClient = null;
            }
            //reception d'une erreur sur la communication avec le serveur
            @Override
            public void onError(Exception e) {
                Log.i("Websocket", "Error " + e.getMessage());
                ReConnect = 0;
            }
        };
        // demande de connexion
        sConnection.setChecked (false);
        sConnection.getThumbDrawable ( ).setColorFilter (android.graphics.Color.RED, PorterDuff.Mode.MULTIPLY);
        mWebSocketClient.connect ( );
    }
}
