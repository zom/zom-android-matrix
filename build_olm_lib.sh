cd ..
rm -rf olm
git clone http://git.matrix.org/git/olm.git/
cd olm/android
echo ndk.dir=$NDK > local.properties
./gradlew assembleRelease

cd ../../keanuapp-android
cp ../olm/android/olm-sdk/build/outputs/aar/olm-sdk-release-*.aar app/libs/olm-sdk.aar
