#include <jni.h>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include "fasttext.h"

namespace {
class Detector final : public fasttext::FastText {
 public:
  void load(std::istream& stream) {
    if (!checkModel(stream)) throw std::invalid_argument("Invalid language model");
    loadModel(stream);
  }
};
std::mutex mutex;
std::unique_ptr<Detector> detector;

std::string bytes(JNIEnv* env, jbyteArray input) {
  const auto size = env->GetArrayLength(input);
  std::string result(size, '\0');
  env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte*>(result.data()));
  return result;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_dergigi_ants_FastTextLanguage_nativeLoad(JNIEnv* env, jobject, jbyteArray model) {
  std::lock_guard<std::mutex> lock(mutex);
  if (detector) return JNI_TRUE;
  try {
    std::istringstream stream(bytes(env, model), std::ios::binary);
    if (env->ExceptionCheck()) return JNI_FALSE;
    auto loaded = std::make_unique<Detector>();
    loaded->load(stream);
    detector = std::move(loaded);
    return JNI_TRUE;
  } catch (const std::exception&) { return JNI_FALSE; }
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_dergigi_ants_FastTextLanguage_nativePredict(JNIEnv* env, jobject, jbyteArray text) {
  std::lock_guard<std::mutex> lock(mutex);
  if (!detector) return nullptr;
  try {
    std::istringstream stream(bytes(env, text));
    if (env->ExceptionCheck()) return nullptr;
    std::vector<std::pair<fasttext::real, std::string>> predictions;
    detector->predictLine(stream, predictions, 3, 0.0f);
    std::ostringstream result;
    for (const auto& prediction : predictions)
      result << prediction.second.substr(9) << '\t' << prediction.first << '\n';
    return env->NewStringUTF(result.str().c_str()); // Labels and scores are ASCII.
  } catch (const std::exception&) { return nullptr; }
}
