# Language filtering

## Bundled detector

fastText v0.9.2 sources are vendored unmodified under `app/src/main/cpp/fasttext`, excluding the CLI entry point. Source: https://github.com/facebookresearch/fastText/releases/tag/v0.9.2 (MIT).

Release archive SHA-256: `7ea4edcdb64bfc6faaaec193ef181bdc108ee62bb6a04e48b2e80b639a99e27e`.

The unmodified `lid.176.ftz` model is from https://dl.fbaipublicfiles.com/fasttext/supervised-models/lid.176.ftz . SHA-256: `8f3472cfe8738a7b6099e8e999c3cbfae0dcd15696aac7d7738a8039db603e83`. Model attribution: Facebook fastText, trained on Wikipedia, Tatoeba and SETimes; distributed under CC-BY-SA 3.0. See https://fasttext.cc/docs/en/language-identification.html and the license copies in `licenses/` and APK `META-INF/`.

Reference: Armand Joulin, Edouard Grave, Piotr Bojanowski and Tomas Mikolov, *Bag of Tricks for Efficient Text Classification*, 2016; Armand Joulin et al., *FastText.zip: Compressing text classification models*, 2016.

The C++ model is loaded lazily once per process under a mutex, only from the checksum-verified bundled asset. JNI input uses UTF-8 bytes. Detection runs off the main thread; no content, detection requests or metrics are sent to a detection service. Model/library failures return unknown. Native build dependencies are pinned to Android NDK 27.2.12479018 and CMake 3.22.1; shared libraries use 16 KB page alignment.
