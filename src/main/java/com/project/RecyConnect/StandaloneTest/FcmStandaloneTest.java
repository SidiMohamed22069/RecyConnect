package com.project.RecyConnect.StandaloneTest;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import java.io.FileInputStream;

public class FcmStandaloneTest {

    public static void main(String[] args) throws Exception {

        String serviceAccountPath =
                "src/main/resources/recyconnect-24ffd-01853b004f4e.json";

        FileInputStream serviceAccount =
                new FileInputStream(serviceAccountPath);

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(
                        GoogleCredentials.fromStream(serviceAccount)
                )
                .build();

        FirebaseApp.initializeApp(options);

        String fcmToken = "fU0rgwP4VE9QlvY0aDYd1W:APA91bG7A82npzVgd1P4aWM6WU8hUKWm-cfcbwYu-1ahivaOxtwHcajweiwbunDrnm2lBNk_8iUuBZUSYqYUHyP0BzqpUcIQg0AlMAaji7Up4Mt1L4HzU7A";

        Message message = Message.builder()
                .setToken(fcmToken)

                // Notification visible
                .setNotification(
                        Notification.builder()
                                .setTitle("Test RecyConnect")
                                .setBody("Ceci est une notification iOS de test")
                                .build()
                )

                // Configuration APNs
                .setApnsConfig(
                        ApnsConfig.builder()
                                .putHeader("apns-priority", "10")
                                .putHeader("apns-push-type", "alert")
                                .setAps(
                                        Aps.builder()
                                                .setAlert(
                                                        ApsAlert.builder()
                                                                .setTitle("Test RecyConnect")
                                                                .setBody("Ceci est une notification iOS de test")
                                                                .build()
                                                )
                                                .setSound("default")
                                                .build()
                                )
                                .build()
                )

                // Données supplémentaires pour Flutter
                .putData("type", "TEST")
                .putData("timestamp",
                        String.valueOf(System.currentTimeMillis()))

                .build();

        String response =
                FirebaseMessaging.getInstance().send(message);

        System.out.println(
                "✅ Message envoyé avec succès! ID: " + response
        );
    }
}