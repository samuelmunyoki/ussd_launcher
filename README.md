# ussd_launcher
A Flutter plugin to launch USSD requests and manage multi-step USSD sessions on Android directly from your application.


## Features
- Launch simple USSD requests
- Manage multi-step USSD sessions
- Check and request necessary accessibility permissions
- Compatibility with Android devices (API level 26+)
- Handle USSD responses and errors gracefully.
- Open accessibility settings if the service is not enabled.
- Get information about the SIM card


## Installation
Add `ussd_launcher` as a dependency in your `pubspec.yaml` file:


## Configuration
### Android
You will need to add the following permissions to your Android manifest file.
```XML
    <uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
```


### For Android multi-session USSD
Add the USSD dialog accessibility service to your Android Manifest
```XML
<application>
    ...
    <service
        android:name="com.kavina.ussd_launcher.UssdAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="false">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
    </service>
</application>
```


## Usage
### Example
```dart
import 'package:flutter/material.dart';
import 'package:ussd_launcher/ussd_launcher.dart';
// ignore: depend_on_referenced_packages
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: DefaultTabController(
        length: 2,
        child: Scaffold(
          appBar: AppBar(
            title: const Text('USSD Launcher Demo'),
            bottom: const TabBar(
              tabs: [
                Tab(text: 'Single Session'),
                Tab(text: 'Multi Session'),
              ],
            ),
          ),
          body: const TabBarView(
            children: [
              SingleSessionTab(),
              MultiSessionTab(),
            ],
          ),
        ),
      ),
    );
  }
}

class SingleSessionTab extends StatefulWidget {
  const SingleSessionTab({super.key});

  @override
  _SingleSessionTabState createState() => _SingleSessionTabState();
}

class _SingleSessionTabState extends State<SingleSessionTab> {
  final TextEditingController _controller = TextEditingController();
  String _ussdResponse = '';
  List<Map<String, dynamic>> _simCards = [];
  int? _selectedSimId;

  @override
  void initState() {
    super.initState();
    _loadSimCards();
  }

  Future<void> _loadSimCards() async {
    var status = await Permission.phone.request();
    if (status.isGranted) {
      try {
        final simCards = await UssdLauncher.getSimCards();
        print("simCards --------------------- $simCards");

        setState(() {
          _simCards = simCards;
          if (simCards.isNotEmpty) {
            _selectedSimId = simCards[0]['subscriptionId'] as int?;
          }
        });
      } catch (e) {
        print("Error loading SIM cards: $e");
      }
    } else {
      print("Phone permission is not granted");
    }
  }

  Future<void> _sendUssdRequest() async {
    try {
      final response = await UssdLauncher.launchUssd(_controller.text, _selectedSimId);
      setState(() {
        _ussdResponse = response;
      });
    } catch (e) {
      setState(() {
        _ussdResponse = 'Error: ${e.toString()}';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        children: [
          // Real-time display of subscriptionId
          Text('Selected SIM ID: ${_selectedSimId ?? "None"}',
              style: const TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          DropdownButton<int>(
            value: _selectedSimId,
            hint: const Text('Select SIM'),
            items: _simCards.map((sim) {
              return DropdownMenuItem<int>(
                value: sim['subscriptionId'],
                child: Text("${sim['displayName']} (${sim['carrierName']})"),
              );
            }).toList(),
            onChanged: (value) {
              setState(() {
                _selectedSimId = value;
              });
            },
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _controller,
            decoration: const InputDecoration(
              labelText: 'Enter USSD Code',
              hintText: 'e.g. *880#',
            ),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _sendUssdRequest,
            child: const Text('Launch Single Session USSD'),
          ),
          const SizedBox(height: 16),
          const Text('USSD Response:'),
          Text(_ussdResponse),
        ],
      ),
    );
  }
}

class MultiSessionTab extends StatefulWidget {
  const MultiSessionTab({super.key});

  @override
  _MultiSessionTabState createState() => _MultiSessionTabState();
}

class _MultiSessionTabState extends State<MultiSessionTab> {
  final TextEditingController _ussdController = TextEditingController();
  final List<TextEditingController> _optionControllers = [];
  String _dialogText = '';
  bool _isLoading = false;
  List<Map<String, dynamic>> _simCards = [];
  int? _selectedSimId;

  @override
  void initState() {
    super.initState();
    _loadSimCards();
  }

  Future<void> _loadSimCards() async {
    var status = await Permission.phone.request();
    if (status.isGranted) {
      final simCards = await UssdLauncher.getSimCards();
      setState(() {
        _simCards = simCards;
        if (simCards.isNotEmpty) {
          _selectedSimId = simCards[0]['subscriptionId'];
        }
      });
    } else {
      print("Phone permission is not granted");
    }
  }

  void _launchMultiSessionUssd() async {
    setState(() {
      _isLoading = true;
      _dialogText = '';
    });

    try {

      String? res1 = await UssdLauncher.multisessionUssd(
        code : _ussdController.text,
        subscriptionId : (_selectedSimId ?? -1),
      );
      _updateDialogText('Initial Response: \n $res1');

      await Future.delayed(const Duration(seconds: 2));

      for (var controller in _optionControllers) {
        String? res = await UssdLauncher.sendMessage(controller.text);
        _updateDialogText(
            '\nResponse after sending "${controller.text}": \n $res');
        await Future.delayed(const Duration(seconds: 1));
      }

      await UssdLauncher.cancelSession();
      _updateDialogText('\nSession cancelled');
    } catch (e) {
      _updateDialogText('\nError: ${e.toString()}');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  void _updateDialogText(String newText) {
    setState(() {
      _dialogText += newText;
    });
  }

  void _addOptionField() {
    setState(() {
      _optionControllers.add(TextEditingController());
    });
  }

  void _removeOptionField() {
    if (_optionControllers.isNotEmpty) {
      setState(() {
        _optionControllers.last.dispose();
        _optionControllers.removeLast();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: SingleChildScrollView(
        child: Column(
          children: [
            // Real-time display of subscriptionId
            Text('Selected SIM ID: ${_selectedSimId ?? "None"}',
                style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 16),
            DropdownButton<int>(
              value: _selectedSimId,
              hint: const Text('Select SIM'),
              items: _simCards.map((sim) {
                return DropdownMenuItem<int>(
                  value: sim['subscriptionId'],
                  child: Text("${sim['displayName']} (${sim['carrierName']})"),
                );
              }).toList(),
              onChanged: (value) {
                setState(() {
                  _selectedSimId = value;
                });
              },
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _ussdController,
              decoration: const InputDecoration(labelText: 'Enter USSD Code'),
            ),
            ..._optionControllers.asMap().entries.map((entry) {
              return Padding(
                padding: const EdgeInsets.only(top: 8.0),
                child: TextField(
                  controller: entry.value,
                  decoration:
                  InputDecoration(labelText: 'Option ${entry.key + 1}'),
                ),
              );
            }),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton(
                  onPressed: _addOptionField,
                  child: const Text('Add Option'),
                ),
                ElevatedButton(
                  onPressed:
                  _optionControllers.isNotEmpty ? _removeOptionField : null,
                  child: const Text('Remove Option'),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _isLoading ? null : _launchMultiSessionUssd,
              child: const Text('Launch Multi-Session USSD'),
            ),
            const SizedBox(height: 16),
            const Text('USSD Response:'),
            Text(_dialogText),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    for (var controller in _optionControllers) {
      controller.dispose();
    }
    _ussdController.dispose();
    super.dispose();
  }
}
```

### Available Methods
- `launchUssd`: Launches a simple USSD request and returns the response as a string. Compatible with Android 8+ (SDK 26+). Il est aussi possible d'envoyer la demande en une seul fois, c'est à dire : `UssdLauncher.launchUssd("*173*2#", -1);`

- `multisessionUssd`: Initiates a multi-step USSD session and returns the initial response.

- `sendMessage`: Sends a message in an ongoing multi-step USSD session.

- `cancelSession`: Terminates the current multi-step USSD session.

- `isAccessibilityPermissionEnabled`: Checks if accessibility permissions are enabled.

- `openAccessibilitySettings`: Opens the device's accessibility settings.


<!-- 
Pour les USSD multisessions, la demande sera envoyée (dans une version ultérieure) en une seule fois, c'est-à-dire : 

```dart
UssdAdvanced.sendAdvancedUssd(code: "*173*2#", subscriptionId: -1);
```
-->


### SIM Card Selection
You can select the SIM card to use by providing the `subscriptionId`. The value `-1` corresponds to the phone's default setting. This feature is compatible with Android 6+ and uses the default value if the SDK is lower.


## Important Notes
- This plugin requires the accessibility service to be enabled on the Android device to function properly.
- Multi-step USSD sessions may vary depending on mobile operators and countries.
- Make sure to properly handle exceptions and error cases in your application.


## Limitations
- This plugin works only on Android.
- Compatibility may vary depending on Android versions and device manufacturers.


## Contribution
Contributions are welcome! Feel free to open an issue or submit a pull request on our [GitHub repository](https://github.com/codianselme/ussd_launcher).


<!-- 
## Licence

Ce projet est sous licence MIT. Voir le fichier [LICENSE](https://github.com/codianselme/ussd_launcher/blob/main/LICENSE) pour plus de détails.
-->