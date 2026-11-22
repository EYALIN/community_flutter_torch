#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint community_flutter_torch.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'community_flutter_torch'
  s.version          = '2.0.1'
  s.summary          = 'Torch on/off, strength levels and a state stream for Flutter.'
  s.description      = <<-DESC
Torch control for Flutter: on/off, 10 strength levels and an isTorchActive state stream.
                       DESC
  s.homepage         = 'https://github.com/EYALIN/community_flutter_torch'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Community Flutter Plugins' => 'community-flutter-plugins@users.noreply.github.com' }
  s.source           = { :path => '.' }
  s.source_files = 'community_flutter_torch/Sources/community_flutter_torch/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '15.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'

  # If your plugin requires a privacy manifest, for example if it uses any
  # required reason APIs, update the PrivacyInfo.xcprivacy file to describe your
  # plugin's privacy impact, and then uncomment this line. For more information,
  # see https://developer.apple.com/documentation/bundleresources/privacy_manifest_files
  # s.resource_bundles = {'community_flutter_torch_privacy' => ['community_flutter_torch/Sources/community_flutter_torch/PrivacyInfo.xcprivacy']}
end
