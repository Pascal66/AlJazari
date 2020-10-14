#include <jni.h>
#include <string>

#include "opencv2/core/core.hpp"
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>

using namespace std;
using namespace cv;

extern "C" JNIEXPORT jstring JNICALL
Java_hujra_baari_aljazari_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Native C code is compiling alright.hoohoo";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
{
void JNICALL Java_hujra_baari_aljazari_NightVisionActivity_NativeCanny(JNIEnv *env, jobject instance,
                                                                              jlong matAddrGray,
                                                                              jint nbrElem) {
    Mat &mGr = *(Mat *) matAddrGray;
    /* for (int k = 0; k < nbrElem; k++) {
         int i = rand() % mGr.cols;
         int j = rand() % mGr.rows;
         mGr.at<uchar>(j, i) = 255;
     }
 */
    //cv::Canny(mGr,mGr,35,90);
    cv::equalizeHist( mGr, mGr );
}
}
