# Add project specific ProGuard rules here.

# Release optimization item (production readiness #13): isMinifyEnabled/isShrinkResources are
# on (app/build.gradle.kts). No custom keep rules were added here because none were found to be
# needed -- verified empirically by inspecting the R8 output of a real assembleProdRelease build
# (app/build/outputs/mapping/prodRelease/mapping.txt), not assumed:
#   - Retrofit interfaces (e.g. AuthApi) keep their methods, parameter types, and generic
#     signatures -- covered by retrofit's own bundled consumer-rules.pro.
#   - kotlinx.serialization @Serializable DTOs (e.g. ResumeAnalysisDto) keep every field and
#     their generated $serializer/Companion classes -- covered by kotlinx-serialization-json's
#     own bundled consumer-rules.pro.
#   - HomeViewModel, the one screen using the reflection-based no-arg viewModel() factory
#     (every other screen passes an explicit viewModelFactory { ... }), keeps its <init>() --
#     R8 traces the HomeViewModel::class.java literal into androidx's
#     Class.getDeclaredConstructor().newInstance() call and retains it automatically.
# If a new DTO, API interface, or reflectively-constructed class is added later, re-check
# mapping.txt for it after a release build rather than assuming this still holds.
