# Mobile Connect Demonstration Application

Mobile Connect is a secure universal log-in solution. Simply by matching the user to their mobile phone, Mobile Connect allows them to log-in to websites and applications quickly without the need to remember passwords and usernames. It’s safe, secure and no personal information is shared without permission.

This application illustrates how mobile connect operates in a nutshell.

**How to run the application**

- Before you can start using Mobile Connect you need the credentials that allow your application to interact with the Mobile Connect APIs. Registering an application on the developer portal will create a set of credentials that you can use in the Sandbox environment and a set that you can use in the Production environment. Follow this [link](https://developer.mobileconnect.io/make-your-first-call) to register your application.

- Add the credentials obtained to src/main/java/com/gsma/mobileconnect/demo/App.java
```
  // Registered application client id
  mobileConnectConfig.setClientId("xxxxx");

  // Registered application client secret
  mobileConnectConfig.setClientSecret("xxxxx");
 
  // URL of the Mobile Connect Discovery End Point
  mobileConnectConfig.setDiscoveryURL("xxxxx");
```
- For testing purpose configure sandbox environment that responds exactly as the Mobile Connect live environment. Configure sandbox environment from [here](https://developer.mobileconnect.io/using-the-sandbox).

- Ensure you have build the mobile-connect-sdk jar using Maven. If not clone and build from [here](https://github.com/Mobile-Connect/java-sdk-v1/tree/master/mobile-connect-sdk).
```
mvn clean install
```
- Run the MobileConnect demo.
```
mvn spring-boot:run
```
- Open your browser and go to http://localhost:8080.
