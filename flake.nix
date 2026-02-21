{
  description = "BusPirate App";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ "35.0.0" "34.0.0" ];
          platformVersions = [ "35" "34" ];
          abiVersions = [ "arm64-v8a" "x86_64" ];
          includeNDK = false;
          includeEmulator = false;
        };

        androidSdk = androidComposition.androidsdk;

        # Wrapper script that sets up environment and uses steam-run for aapt2
        buildScript = pkgs.writeShellScriptBin "build-apk" ''
          export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
          export ANDROID_SDK_ROOT="${androidSdk}/libexec/android-sdk"
          export JAVA_HOME="${pkgs.jdk17}"
          export PATH="${androidSdk}/libexec/android-sdk/platform-tools:${pkgs.jdk17}/bin:${pkgs.gradle}/bin:$PATH"
          exec ${pkgs.steam-run}/bin/steam-run ./gradlew "$@"
        '';
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            androidSdk
            gradle
            jdk17
            kotlin
            steam-run
            buildScript
          ];

          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
          JAVA_HOME = "${pkgs.jdk17}";

          shellHook = ''
            export PATH="${androidSdk}/libexec/android-sdk/platform-tools:$PATH"
            echo "YouBike Widget development environment"
            echo "Android SDK: $ANDROID_HOME"
            echo "Java: $JAVA_HOME"
            echo ""
            echo "Commands:"
            echo "  build-apk assembleDebug   - Build debug APK"
            echo "  build-apk assembleRelease - Build release APK"
            echo "  adb install -r app/build/outputs/apk/debug/app-debug.apk"
          '';
        };
      }
    );
