#!/usr/bin/env python3
"""GameLingo v0.5 structural regression checks.

These are static contract checks and DO NOT substitute physical PS4/PS5, Xbox,
iPhone, Android or Google TV integration tests.
"""
from pathlib import Path
import json
import subprocess
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
IOS=ROOT/"ios/GameLingoIOS/App"
TOOLS=ROOT/"tools"

def code(path):
    return path.read_text(encoding="utf-8")

class GameLingoIntegrationContracts(unittest.TestCase):
    def test_independent_camera_source(self):
        camera = code(IOS/"GameLingoCameraScreen.swift")
        home = code(IOS/"GameLingoHome.swift")
        setup = code(IOS/"GameLingoSetupView.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        self.assertIn("AVCaptureDevice.requestAccess(for: .video)", camera)
        self.assertIn("alwaysDiscardsLateVideoFrames = true", camera)
        self.assertIn("request.regionOfInterest", camera)
        self.assertIn("model.translateCameraText", camera)
        self.assertIn(".onDisappear { state.stop() }", camera)
        self.assertNotIn("for: .audio", camera)
        self.assertIn("(cameraSelected || consoleRegistered) && televisionPaired", setup)
        self.assertIn("GameLingoCameraScreen(model: model, devices: devices, receiver: model.tv)", home)
        self.assertIn("AVCaptureVideoPreviewLayer", camera)
        self.assertIn("self.analysisQueue", camera)
        self.assertNotIn("guard confirmations >= 2", camera)
        self.assertIn("if cameraSource", home)
        self.assertIn("state.setTV(tvReady)", camera)
        self.assertIn("guard !isRunning, verifiedTelevisionOutput", model)
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        first_run = code(TOOLS/"GameLingoFirstRun.kt")
        wire = code(TOOLS/"wire_android_devices.py")
        self.assertIn("STRATEGY_KEEP_ONLY_LATEST", android)
        self.assertIn("PreviewView.ScaleType.FIT_CENTER", android)
        self.assertIn("live, use)", android)
        self.assertNotIn("confirmations < 2", android)
        self.assertIn("if (!TvProfily(this).pouzeTelefon) enableTV()", android)
        self.assertIn("receiveFullImage(text, blocks)", android)
        self.assertIn("receiveFullImage(text: text, blocks: blocks)", camera)
        self.assertIn("cameraSourceSelected", code(TOOLS/"redesign_gamelingo.py"))
        self.assertIn("Prekladac(this, to, from)", android)
        self.assertIn("Manifest.permission.CAMERA", android)
        self.assertNotIn("RECORD_AUDIO", android)
        self.assertIn("override fun onStop() { releaseCamera();", android)
        self.assertIn('putBoolean("cameraSourceSelected", consoleChoice == 2)', first_run)
        self.assertNotIn("connectCamera", first_run)
        self.assertNotIn("connectCamera", wire)
        self.assertNotIn("cameraAfterSetup", home)
        for source_ui in (home, setup, first_run, code(TOOLS/"redesign_gamelingo.py")):
            self.assertIn("Vybrat zařízení", source_ui)
        self.assertEqual(json.loads(code(ROOT/"shared/locales/en.json"))["Vybrat zařízení"], "Select device")
        self.assertNotIn('GLPrimaryButton(title: "Překlad kamerou"', home)
        self.assertNotIn('GLPrimaryButton(title: "Překlad kamerou"', setup)
        self.assertIn("GLPlatformBadge(family: nil, size: 57, isCamera: true)", setup)
        self.assertIn("onSourceSelected(cameraSelected)", setup)
        self.assertEqual(home.count("cameraSheet = true"), 1)
        self.assertIn("selectionOverlay", home)
        self.assertIn('Text("Zrušit")', home)
        self.assertIn('sourcePicker ? "Vybrat zařízení" : "Výstup titulků"', home)
        self.assertNotIn('.confirmationDialog("Vybrat zařízení"', home)
        self.assertNotIn('.confirmationDialog("Výstup titulků"', home)
        self.assertIn('GameLingoLanguages.ui(activity, "Zrušit")', code(TOOLS/"GameLingoVisualKit.kt"))
        self.assertEqual(code(TOOLS/"redesign_gamelingo.py").count("GameLingoVisualKit.selectSourceOrOutput"), 3)
        self.assertNotIn("cameraSelected = true\n                chosenFamily = nil\n                devices.subtitleOutput", setup)
        self.assertNotIn("consoleChoice = 2\n                        tvChoice = 0", first_run)
        self.assertIn("GameLingoCameraActivity.open(this)", code(TOOLS/"redesign_gamelingo.py"))
        self.assertIn('android:exported="false"', wire)
        for key in ("Překlad přes původní text", "Jazyk textu", "Obnovit oblast", "Náhled obrazovky"):
            self.assertIn(key, camera)
            self.assertIn(key, android)
        self.assertIn("self.previousVisible.contains(original)", camera)
        self.assertIn("original in previousVisible", android)
        self.assertIn("GameLingoTVPairing.connected(tv.ip)", android)
        self.assertIn("Propojeni.Vysilac.cameraCaption", android)
        self.assertIn("if (RemotePlayService.aktivni || PrijemService.bezi) return@synchronized", wire)

    def test_camera_adaptive_full_image_overlay(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        for key in ("GLCameraPalette(cg)", "previous.text +", "previous.rect.union(rect)",
                    "overlay.ink.map { GLCameraPalette.color($0) } ?? GLCameraPalette.foreground(overlay.background)",
                    "GLCameraPalette.color(overlay.background)",
                    ".rotationEffect(.radians(projection == nil ? overlay.angle : 0))",
                    "self.overlayCache[$0.text] == nil",
                    "wholeImage ? 0.35 : 0.45",
                    "preview.aspectRatio(fullImage ? 9.0 / 16.0 : 0.9"):
            self.assertIn(key, ios)
        for key in ("sampleBackground(bitmap, rect)", "block.lines.size",
                    "task.result.textBlocks.flatMap { it.lines }",
                    "Color.luminance(overlay.background)", "StaticLayout.Builder.obtain",
                    "canvas.rotate(overlay.angle", "if (region.full) 350L else 450L",
                    "root.addView(outputViewport, LinearLayout.LayoutParams(-1, px(108)))"):
            self.assertIn(key, android)

    def test_standalone_dialogue_absence_is_time_based(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        window = code(TOOLS/"GameLingoDialogueWindow.kt")
        self.assertIn("if (storyMode) blankFrames >= 2 else dialogueAbsence.observeEmpty(now)", capture)
        self.assertIn("if (confirmed && blankSince == 0L)", capture)
        self.assertIn("return now - first >= 800L", window)
        self.assertGreaterEqual(capture.count("dialogueAbsence.reset()"), 2)
        self.assertIn("val key = GameLingoDialogueText.normalize(source)", window)

    def test_automatic_ocr_filters_ignored_lines_before_grouping(self):
        pipeline = code(IOS/"CaptionPipeline.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        automatic = code(TOOLS/"GameLingoAutomaticOCR.kt")
        self.assertIn("filterMenus: filterMenuText, ignoredLines: ignoredBlueLines", model)
        self.assertIn("YellowOCRFilter.keepsAutomaticDialogue(text, filterMenus: filterMenus, ignored: ignored)", pipeline)
        self.assertIn("GameLingoOCRConfig.ignoredBlueEntries", automatic)
        self.assertLess(automatic.index("val lines = b.lines.filter"), automatic.index('val text=lines.joinToString'))
        self.assertIn("GameLingoYellowOCRFilter.keepsAutomaticDialogue(it.text, filterMenus, ignored)", automatic)

    def test_short_dialogue_geometry_respects_selected_mode(self):
        redesign = code(TOOLS/"redesign_gamelingo.py")
        wire = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn('select=base/SRC/"VyberTitulku.kt"', redesign)
        self.assertIn('GameLingoDialogueText.readableLine(it.text, GameLingoOCRConfig.mode == "street")', redesign)
        self.assertNotIn('geometry = folder / "VyberTitulku.kt"', wire)
        self.assertIn("fun readableLine", code(TOOLS/"GameLingoDialogueText.kt"))
        self.assertIn("if text == accepted", code(IOS/"CaptionStability.swift"))

    def test_camera_full_image_failure_does_not_block_other_blocks(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        for source in (ios, android):
            self.assertIn("blockRetryAt", source)
            self.assertIn("consecutiveFailures >= 2", source)
            self.assertIn("attempted", source)
        self.assertIn("!attempted.contains($0.text)", ios)
        self.assertIn("it.text !in attempted", android)
        self.assertIn("self.blockRetryAt[original] = ProcessInfo.processInfo.systemUptime + 5", ios)
        self.assertIn("blockRetryAt[original] = SystemClock.elapsedRealtime() + 5000L", android)
        self.assertIn("blockRetryAt.removeAll()", ios)
        self.assertIn("blockRetryAt.clear()", android)

    def test_camera_reconstructs_only_supported_smooth_surfaces(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        for key in ("struct GLCameraSurface: Sendable", "func reconstructedSurface(for rect: CGRect",
                    "(12...100).contains(variation)", "contrast(a) >= 4.5, contrast(b) >= 4.5",
                    "guard agreement >= 8 else { return nil }", "let horizontal = abs(block.angle) < 0.06",
                    "blocks[index].surface = palette.reconstructedSurface", "surface: block.surface",
                    "if let surface = overlay.surface", "LinearGradient(colors:",
                    "GLCameraPalette.color(overlay.background)"):
            self.assertIn(key, ios)
        for key in ("data class CameraSurface", "fun reconstructedSurface(bitmap: Bitmap",
                    "distance(a, b) !in 12..100", "contrast(a) < 4.5f || contrast(b) < 4.5f",
                    "if (agreement < 8) return null", "val horizontal = kotlin.math.abs(angle) < 3.44f",
                    "reconstructedSurface(bitmap, rect, background, ink)", "block.ink, block.surface",
                    "overlay.surface?.let", "android.graphics.LinearGradient(", "paint.shader = null"):
            self.assertIn(key, android)

    def test_camera_texture_weight_and_perspective_retry(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        for key in ("struct GLCameraQuad: Sendable", "guard cross > 0.001",
                    "t.m14 = g / size.width", "t.m24 = h / size.height",
                    ".projectionEffect(projection ??", "textureAndWeight(for rect:",
                    "let inkMask = source.map", "mask.filter({$0}).count <= w*h*65/100",
                    "donors.allSatisfy", "guard let found else { return (nil,weight) }",
                    "overlay.weight == 2 ? .bold", "CIPerspectiveCorrection",
                    "attempted: &attemptedRectification", "original.confidence + 0.08",
                    "results.count == 1", "if wholeImage && !attemptedRectification"):
            self.assertIn(key, ios)
        for key in ("data class CameraQuad", "setPolyToPoly(src, 0, dst, 0, 4)",
                    "canvas.concat(projection)", "fun textureAndWeight(bitmap:",
                    "val inkMask = BooleanArray", "mask.count { it } > w*h*65/100",
                    "donors.any { color ->", "found ?: return CameraAppearance(null,weight)",
                    "2 -> 700; 1 -> 600; else -> 400", "fun rectifiedCrop(bitmap:",
                    "warp.setPolyToPoly(src,0,dst,0,4)", "line.confidence",
                    "originalScore+.08f", "finishFrame(index, improved)",
                    "validFrame() && region.full && task.isSuccessful",
                    "rectified.recycle()"):
            self.assertIn(key, android)

    def test_camera_ink_confidence_and_reading_order(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        for key in ("recognized.confidence >= 0.30", "!wholeImage || recognized.confidence",
                    "palette?.ink(for: rect, background: background)",
                    "best.count >= 8", ">= 4.5 ? rgb : nil",
                    "GLCameraPalette.compatibleInk(previous.ink, block.ink)",
                    "wholeImage ? Self.columnOrder(rows) : rows",
                    "sorted.count - index >= 2"):
            self.assertIn(key, ios)
        for key in ("block.lines.map { it.confidence }.filter { it > 0f }",
                    "scores.average() < .30", "sampleInk(bitmap, rect, background)",
                    "best[0] < 8", "contrast(it) >= 4.5f",
                    "overlay.ink ?: (if", "if (region.full) columnOrder(blocks) else blocks",
                    "sorted.size - i >= 2"):
            self.assertIn(key, android)

    def test_camera_live_block_pipeline_optimization(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        for key in ("visible.intersection(previousVisible)", "visible.isDisjoint(with: previousVisible)",
                    "self.translationRequest == request", "self.previousVisible.contains(original)",
                    "self.overlayCache[$0.text] == nil", "cacheOrder.removeFirst()",
                    "private let textRequest = VNRecognizeTextRequest()", "autoreleasepool {",
                    "min(0.7, max(base", "stableFrames >= 3 ? 0.55",
                    "candidate.split(whereSeparator: { $0.isWhitespace })"):
            self.assertIn(key, ios)
        for key in ("visible.intersect(previousVisible)", "request == translationRequest",
                    "original in previousVisible", "it.text !in overlayCache",
                    "overlayCache.keys.firstOrNull()", "output.post { next() }",
                    "analysisCost * 5 / 4", "coerceAtMost(700L)", "Bitmap.createScaledBitmap",
                    "if (fallback !== bitmap) bitmap.recycle()", "normalized(candidate) == normalized(text)"):
            self.assertIn(key, android)

    def test_camera_overlay_reuses_only_visible_recognized_text(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        self.assertIn("currentBlocks.enumerated().compactMap", ios)
        self.assertIn("overlayCache[block.text]", ios)
        self.assertIn("overlayCache.count >= 256", ios)
        self.assertIn("if overlaySource != source", ios)
        self.assertIn("currentBlocks.mapNotNull", android)
        self.assertIn("overlayCache[block.text]", android)
        self.assertIn("overlayCache.size >= 256", android)
        self.assertIn("if (overlaySource != from)", android)

    def test_camera_torch_is_icon_only_in_header(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        self.assertIn("ToolbarItem(placement: .topBarTrailing)", ios)
        self.assertIn('.accessibilityLabel("Svítilna")', ios)
        self.assertNotIn('Label("Svítilna",', ios)
        self.assertIn("torchButton = ImageButton(this)", android)
        self.assertIn('contentDescription = tr("Svítilna")', android)
        self.assertIn("addView(torchButton, LinearLayout.LayoutParams(px(48), px(48)))", android)
        self.assertNotIn("actions.addView(torchButton", android)

    def test_camera_continuous_autofocus(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        self.assertIn("device.focusMode = .continuousAutoFocus", ios)
        self.assertIn("device.autoFocusRangeRestriction = .none", ios)
        self.assertIn("device.focusMode = .autoFocus", ios)
        self.assertIn("device.exposureMode = .continuousAutoExposure", ios)
        self.assertIn("self.configureAutomaticFocus()", ios)
        self.assertLess(ios.index("self.session.startRunning()"), ios.index("self.configureAutomaticFocus()"))
        self.assertIn("camera?.cameraControl?.cancelFocusAndMetering()", android)
        self.assertLess(android.index("camera = p.bindToLifecycle"), android.index("camera?.cameraControl?.cancelFocusAndMetering()"))

    def test_camera_torch_state_and_lifecycle(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        self.assertIn("try device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)", ios)
        self.assertIn("self.session.isRunning, let device = self.device, device.hasTorch", ios)
        self.assertIn("self.torchRequest == request", ios)
        self.assertIn("state.previewSession == nil || state.torchPending", ios)
        self.assertIn("capture?.stop(); resetTorch()", ios)
        self.assertIn("c.cameraInfo.torchState.observe(this)", android)
        self.assertIn("c.cameraInfo.torchState.value != TorchState.ON", android)
        self.assertIn("camera?.cameraInfo?.torchState?.removeObservers(this)", android)
        self.assertIn("torchPending = false; torchFailed = false", android)

    def test_camera_translation_layout_does_not_reflow(self):
        ios = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        self.assertIn("translationPanel", ios)
        self.assertIn(".frame(height: 108)", ios)
        self.assertIn(".safeAreaInset(edge: .bottom", ios)
        self.assertIn("preview.aspectRatio(fullImage ? 9.0 / 16.0", ios)
        self.assertIn("root.addView(outputViewport, LinearLayout.LayoutParams(-1, px(108)))", android)
        self.assertIn("addView(controls, LinearLayout.LayoutParams(-1, -2))", android)
        self.assertNotIn("output.visibility =", android)
        self.assertNotIn("preview.bitmap?.let { it.height.toFloat() / it.width }", android)

    def test_camera_language_selection_is_independent(self):
        camera = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        pipeline = code(IOS/"CaptionPipeline.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        self.assertIn('Text("Automaticky").tag("auto")', camera)
        self.assertIn("NLLanguageRecognizer()", camera)
        self.assertIn("request.supportedRecognitionLanguages()", camera)
        self.assertIn("request.automaticallyDetectsLanguage", camera)
        self.assertIn("GLTargetLanguage.allCases", camera)
        self.assertIn("sourceCode: source.rawValue", pipeline)
        self.assertIn("GLTargetLanguage(rawValue: sourceCode)", pipeline)
        self.assertIn("pipeline.translate(original, sourceCode: source, to: target)", model)
        self.assertIn("LanguageIdentification.getClient()", android)
        self.assertIn("languageIdentifier.identifyLanguage(text)", android)
        self.assertIn("languageIdentifier.close()", android)
        self.assertIn("cameraLanguages", android)
        self.assertIn("translatorSource != from", android)
        self.assertIn("updatePreviewHeight()", android)
        self.assertNotIn("preview.frame(height: 340)", camera)

    def test_camera_region_editor_uses_direct_manipulation(self):
        camera = code(IOS/"GameLingoCameraScreen.swift")
        android = code(TOOLS/"GameLingoCameraActivity.kt")
        for obsolete in ("Vodorovná pozice oblasti", "Šířka oblasti", "Velikost oblasti"):
            self.assertNotIn(obsolete, camera)
            self.assertNotIn(obsolete, android)
        self.assertIn("GLNeonPanel(emphasized: true)", camera)
        self.assertIn("regionEditor(size: proxy.size)", camera)
        self.assertIn("DragGesture(minimumDistance: 0", camera)
        self.assertIn("ForEach(0..<4", camera)
        self.assertIn(".onEnded { _ in commitRegion", camera)
        self.assertIn('AppStorage("gamelingo.camera.regionX")', camera)
        self.assertIn("state.configure(source: source, target: target, region: fullImage ? region : rect", camera)
        self.assertIn("if self.session.isRunning {", camera)
        self.assertIn("self.ready(self.session)", camera)
        self.assertNotIn("self.previewSession = capture.session", camera)
        self.assertIn("previewLayer.frame = bounds", camera)
        self.assertIn("previewLayer.isPreviewing", camera)
        self.assertIn("fallback: state.image", camera)
        self.assertIn("MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP", android)
        self.assertIn("MotionEvent.ACTION_CANCEL", android)
        self.assertIn("onRegionCommitted(Region(false", android)
        self.assertIn("parent.requestDisallowInterceptTouchEvent(true)", android)
        self.assertIn("canvas.drawCircle(cx, cy", android)
        self.assertIn('getSharedPreferences("gamelingo_camera", MODE_PRIVATE)', android)
        self.assertIn("livePreview.previewStreamState.observe(this)", android)
        self.assertIn("PreviewView.StreamState.STREAMING", android)
        self.assertIn('button("Obnovit oblast")', android)

    def test_reset_defaults_preserves_profiles_and_pairing(self):
        ios_model = code(IOS/"PlayCaptionIOSApp.swift")
        reset = ios_model.split("func restoreDefaultSettings() {", 1)[1].split(
            '    @Published var status = "Připraveno"', 1)[0]
        self.assertIn("guard !isRunning else { return }", reset)
        self.assertIn("setAutomaticGameDialogue(false)", reset)
        self.assertIn("updateAutomaticZones(AutomaticOCRZone.initial)", reset)
        self.assertNotIn("appLanguage =", reset)
        for setting in ("ocrMode", "manualOCRBox", "choicesArea",
                        "phoneFontScale", "tvTextColor", "outputWidth"):
            self.assertIn(setting + " =", reset)
        for protected in ("gameProfiles =", "devices =", "xbox =", "playStation =",
                          "removePersistentDomain", "removeAll()"):
            self.assertNotIn(protected, reset)
        ios_settings = code(IOS/"SettingsHomeView.swift")
        self.assertIn("model.restoreDefaultSettings()", ios_settings)
        self.assertIn(".disabled(model.isRunning)", ios_settings)

        android = code(TOOLS/"GameLingoSettings.kt")
        android_reset = android.split("fun restoreDefaultSettings(a: Activity,", 1)[1].split(
            "fun confirmRestoreDefaults", 1)[0]
        self.assertIn("RemotePlayService.aktivni", android_reset)
        self.assertIn("PrijemService.bezi", android_reset)
        self.assertIn("directTVBusy()", android_reset)
        self.assertNotIn("Nastaveni(a).jazyk =", android_reset)
        self.assertNotIn("GameLingoScreenTranslationService", android_reset)
        for pref in ("gamelingo_automatic", "gamelingo_phone_subtitles",
                     "gamelingo_tv_local_style", "gamelingo_tv_parity"):
            self.assertIn(pref, android_reset)
        self.assertEqual(android_reset.count('getSharedPreferences("gamelingo_phone_subtitles"'), 1)
        self.assertIn('.remove("color").remove("opacity").apply()', android_reset)
        self.assertIn("GameLingoOCRConfig.reset(a)", android_reset)
        self.assertIn("Volby.nacti(a)", android_reset)
        self.assertIn("OblastTitulku.nacti(a)", android_reset)
        self.assertNotIn(".clear()", android_reset)
        for protected in ('"tv_profily"', '"gamelingo_game_profiles"',
                          '"gamelingo_pairing"'):
            self.assertNotIn(protected, android_reset)
        self.assertIn("confirmRestoreDefaults(activity) { onRefresh() }", android)
        tv = code(TOOLS/"GameLingoTVSettings.kt")
        self.assertIn("GameLingoSettings.confirmRestoreDefaults(a,", tv)
        self.assertIn("directTVBusy = { GameLingoScreenTranslationService.running }", tv)

    def test_localized_region_editor_preserves_geometry_and_menu_icons(self):
        layout = code(TOOLS/"GameLingoDirectTVLayout.kt")
        phone = code(TOOLS/"GameLingoSettings.kt")
        tv = code(TOOLS/"GameLingoTVSettings.kt")
        self.assertIn('active.text=tr(names[selected])', layout)
        self.assertIn('GameLingoLanguages.ui(context, names[i].substringBefore(" ·"))', layout)
        self.assertIn('KEYCODE_DPAD_LEFT', layout)
        self.assertIn('storedGreen', layout)
        self.assertIn('menu("globe", "Jazyky"', phone)
        self.assertIn('tile("globe", "Jazyky"', tv)

    def test_localized_tv_welcome_qr_and_cache(self):
        ui = code(TOOLS/"GameLingoTVUi.kt")
        qr = code(TOOLS/"GameLingoQRDisplay.kt")
        scanner = code(TOOLS/"GameLingoQRScanner.kt")
        cache = code(TOOLS/"GameLingoTranslationCache.kt")
        self.assertIn('GameLingoLanguages.ui(a, "Vítej v GameLingo TV")', ui)
        self.assertIn('GameLingoLanguages.ui(activity, "Párování pomocí QR")', qr)
        self.assertIn('GameLingoLanguages.ui(activity, "Načti QR kód z GameLingo TV")', scanner)
        self.assertIn('GameLingoLanguages.ui(a, "Vypnout paměť")', cache)
        self.assertIn('QRCodeWriter().encode(target', qr)
        self.assertIn('GameLingoTranslationCache.manage(a, value)', cache)

    def test_localized_help_and_about_preserve_license_and_phone_keys(self):
        help_ui = code(TOOLS/"GameLingoHelp.kt")
        about = code(TOOLS/"GameLingoAbout.kt")
        patch = code(TOOLS/"direct_tv_mode.py")
        self.assertIn('GameLingoLanguages.ui(activity, descriptionKey)', help_ui)
        self.assertIn('text = tr(value)', about)
        self.assertIn('AGPL-3.0', about)
        self.assertIn('notices ?: tr(', about)
        self.assertIn('GameLingoLanguages.ui(c, "Odpárovat")', patch)
        self.assertIn('IPhoneTVPairing.unpair(c, phone.id)', patch)

    def test_localized_device_pairing_and_diagnostics(self):
        device = code(TOOLS/"GameLingoDeviceCenter.kt")
        pairing = code(TOOLS/"GameLingoTVPairing.kt")
        diagnostics = code(TOOLS/"GameLingoDiagnostics.kt")
        self.assertIn('GameLingoLanguages.ui(c, "Přidat zařízení")', device)
        self.assertIn('GameLingoLanguages.ui(activity, "Spárovat")', pairing)
        self.assertIn('GameLingoLanguages.ui(activity, "Ověřit spojení s TV")', diagnostics)
        self.assertIn('setText(initialPin.takeIf', pairing)
        self.assertIn('item.original+"\\n\\n"+item.translated', diagnostics)

    def test_profiles_optional_and_localized_without_translating_names(self):
        phone = code(TOOLS/"GameLingoProfiles.kt")
        tv = code(TOOLS/"GameLingoTVProfiles.kt")
        glossary = code(TOOLS/"GameLingoGlossaryUi.kt")
        self.assertIn('if (names.isEmpty()) { onChosen(); return }', tv)
        self.assertIn('GameLingoLanguages.ui(a, "Aktuální nastavení")', tv)
        self.assertIn('text(activity,name,16f', phone)
        self.assertIn('GameLingoVisualKit.detailRow(a,"gamecontroller",name,', tv)
        self.assertIn('setText(saved?.text.orEmpty())', glossary)

    def test_localized_region_and_appearance_controls(self):
        lang = code(TOOLS/"GameLingoLanguages.kt")
        regions = code(TOOLS/"GameLingoRegions.kt")
        phone = code(TOOLS/"GameLingoSubtitleAppearance.kt")
        tv = code(TOOLS/"GameLingoTVCaptionStyle.kt")
        for label in ("Otevřít editor oblastí", "Velikost písma", "Náhled na TV", "Pozadí titulků"):
            self.assertIn(label, json.loads(code(ROOT/"shared/locales/cs.json")))
        self.assertIn("text = tr(name)", regions)
        self.assertIn("this.text = tr(label)", phone)
        self.assertIn("this.text = GameLingoPhoneSubtitleStyle.sample(activity)", phone)
        self.assertIn("title.text=tr(labelText)", tv)

    def test_android_localized_ocr_translation_and_history(self):
        lang = code(TOOLS/"GameLingoLanguages.kt")
        settings = code(TOOLS/"GameLingoSettings.kt")
        ocr = code(TOOLS/"GameLingoOCRSettings.kt")
        history = code(TOOLS/"GameLingoHistoryUi.kt")
        for label in ("Překládat dialogy", "Režim OCR", "Uchovávat historii", "Výchozí oblast", "Automaticky"):
            self.assertIn(label, json.loads(code(ROOT/"shared/locales/cs.json")))
        self.assertIn("GameLingoLanguages.ui(c, title)", settings)
        self.assertIn("this.text = tr(value)", ocr)
        self.assertIn('text = tr("Uchovávat historii")', history)
        self.assertIn("entry.translated", history)
        self.assertIn("entry.original", history)

    def test_settings_mode_updates_without_reopening(self):
        automatic = code(TOOLS/"GameLingoAutomaticConfig.kt")
        phone = code(TOOLS/"GameLingoSettings.kt")
        tv = code(TOOLS/"GameLingoTVSettings.kt")
        visual = code(TOOLS/"GameLingoVisualKit.kt")
        redesign = code(TOOLS/"redesign_gamelingo.py")
        self.assertIn('putBoolean("enabled", index == 1).apply()', automatic)
        self.assertIn("onChanged()", automatic)
        self.assertIn("onChanged = onRefresh", phone)
        self.assertIn("onRefresh = { openTab(3) }", redesign)
        self.assertRegex(visual, r'tag\s*=\s*"gamelingo-detail"')
        self.assertIn('cards[1].findViewWithTag', tv)
        self.assertIn('cards[3].findViewWithTag', tv)

    def test_mode_page_groups_ocr_translation_and_areas(self):
        home = code(IOS/"GameLingoHome.swift")
        sheet = home.split('.sheet(isPresented: $modeSheet)', 1)[1].split('.sheet(isPresented: $appearanceSheet)', 1)[0]
        self.assertIn("GameLingoTranslationModeView(model: model)", sheet)
        settings = code(IOS/"SettingsHomeView.swift")
        mode = settings.split("struct GameLingoTranslationModeView:", 1)[1].split("struct GameLingoLanguageSettingsView:", 1)[0]
        for marker in ("GLTranslationModePicker", "GameLingoOCRSettingsView",
                       "GameLingoTranslationSettingsView", "GLAutomaticZonesView",
                       "SubtitleSettingsView", "$showRegions"):
            self.assertIn(marker, mode)
        android = code(TOOLS/"GameLingoAutomaticConfig.kt")
        mode = android.split("fun mode(a:", 1)[1].split("fun modePicker(", 1)[0]
        for marker in ("modePicker(", "GameLingoOCRSettings.show(a, ::openAreas)",
                       "GameLingoSettings.showTranslation(a, settings, ::openAreas, onLanguage)",
                       "if (enabled(a)) showZones(a) else onStandardAreas()",
                       'tr("Oblasti obrazovky")', "tr(areaDetail())"):
            self.assertIn(marker, mode)
        for name in ("redesign_gamelingo.py", "GameLingoTVSettings.kt"):
            source = code(TOOLS/name)
            self.assertIn("GameLingoAutomaticConfig.mode(", source)
            self.assertIn("onStandardAreas =", source)
            self.assertIn("onLanguage =", source)

    def test_pairing_cards_have_balanced_side_rails(self):
        for name, markers in (
            ("GameLingoSetupView.swift", (57, 32, 42)),
            ("GameLingoTVSetupScreen.swift", (72, 47)),
            ("GameLingoXboxScreen.swift", (44,)),
            ("PlayStation/GameLingoPlayStationSetup.swift", (46, 44)),
        ):
            with self.subTest(screen=name):
                source = code(IOS/name)
                for width in markers:
                    self.assertIn(f".frame(width: {width}, alignment: .trailing)", source)
                self.assertIn(".frame(maxWidth: .infinity)", source)
                self.assertIn(".multilineTextAlignment(.center)", source)
        device_center = code(TOOLS/"GameLingoDeviceCenter.kt")
        self.assertEqual(device_center.count("LinearLayout.LayoutParams(railSize, railSize)"), 2)
        self.assertIn('android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)', device_center)

    def test_pairing_ip_keyboard_and_ps_pin_dismissal(self):
        ps = code(IOS/"PlayStation/GameLingoPlayStationSetup.swift")
        tv = code(IOS/"GameLingoTVSetupScreen.swift")
        for source, field in ((ps, "IP adresa konzole"), (tv, "IP adresa televize")):
            field_source = source.split(f'TextField("{field}",', 1)[1].split(".textFieldStyle", 1)[0]
            self.assertIn(".keyboardType(.asciiCapable)", field_source)
            self.assertNotIn(".numbersAndPunctuation", field_source)
        self.assertIn(".focused($pinFocused)", ps)
        self.assertIn('Button("Hotovo") { pinFocused = false }', ps)
        self.assertIn("ToolbarItemGroup(placement: .keyboard)", ps)
        self.assertIn("$0.isASCII && $0.isNumber", ps)
        self.assertIn('Label("IP adresa konzole", systemImage: "exclamationmark.triangle")', ps)
        self.assertIn("ps.register(address: host.trimmingCharacters(in: .whitespacesAndNewlines)", ps)

    def test_centered_shared_menu_components(self):
        android = code(TOOLS/"GameLingoVisualKit.kt")
        tv = code(TOOLS/"GameLingoTVUi.kt")
        onboarding = code(TOOLS/"GameLingoFirstRun.kt")
        ios = code(IOS/"GameLingoVisualSystem.swift")
        style = code(IOS/"GameLingoStyle.swift")
        # Check centered label bodies independently of Kotlin whitespace and formatting.
        self.assertRegex(android,
            r'text\s*=\s*title\s+tag\s*=\s*"gamelingo-title"\s+gravity\s*=\s*Gravity.CENTER')
        self.assertRegex(android,
            r'text\s*=\s*detail\s+tag\s*=\s*"gamelingo-detail"\s+gravity\s*=\s*Gravity.CENTER')
        self.assertIn("this.text = text\n        gravity = Gravity.CENTER", tv)
        self.assertIn("val heading = TextView(activity).apply {\n            gravity = Gravity.CENTER", onboarding)
        self.assertIn(".multilineTextAlignment(.center)", ios)
        self.assertIn(".frame(width: 42, alignment: .trailing)", ios)
        self.assertIn(".multilineTextAlignment(.center)", style)

    def test_android_entry_localization_and_first_run_simplification(self):
        import re
        languages = code(TOOLS/"GameLingoLanguages.kt")
        first = code(TOOLS/"GameLingoFirstRun.kt")
        nav = code(TOOLS/"GameLingoNavigation.kt")
        settings = code(TOOLS/"GameLingoSettings.kt")
        tv = code(TOOLS/"GameLingoTVSettings.kt")
        auto = code(TOOLS/"GameLingoAutomaticConfig.kt")
        spec = json.loads(code(ROOT/"shared/mobile-ui.json"))
        catalog = {key: (value, json.loads(code(ROOT/"shared/locales/en.json"))[key])
                   for key, value in json.loads(code(ROOT/"shared/locales/sk.json")).items()}
        self.assertEqual(len(catalog), len(set(catalog)))
        for title in spec["setupSteps"] + spec["settingsSections"] + [tab["title"] for tab in spec["tabs"]]:
            self.assertIn(title, catalog)
        for title in ("Jazyk aplikace", "Jazyk hry", "Jazyk překladu", "Standardní", "Automatický", "Nastavit později", "Pokročilé nastavení"):
            self.assertIn(title, catalog)
        self.assertIn('?: "en"', languages)
        self.assertIn('GameLingoLanguages.ui(activity, destination.title)', nav)
        self.assertEqual(first.count('tr("Spárovat TV")'), 1)
        self.assertIn('2 -> consoleRegistered() && televisionPaired()', first)
        self.assertIn('visibility = android.view.View.GONE', settings)
        self.assertIn('parent = advanced', settings)
        self.assertEqual(len(re.findall(r'^        tile\(', tv, re.M)), 9)
        tv_ui = code(TOOLS/"GameLingoTVUi.kt")
        self.assertIn('val back=action(a,GameLingoLanguages.ui(a,"Zpět")){dialog.dismiss()}', tv_ui)
        self.assertIn('val back = GameLingoTVUi.action(a, GameLingoLanguages.ui(a, "Zpět")) { dialog.dismiss() }', tv)
        self.assertIn('getBoolean("enabled",false)', auto)
        self.assertNotIn('if(i==1) showZones(a)', auto)

    def test_first_run_and_device_library(self):
        home=code(IOS/"GameLingoHome.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        devices=code(IOS/"GameLingoDevices.swift")
        panel=code(IOS/"GameLingoDevicesPanel.swift")
        for label in ('GameLingoSetupView', 'GameLingoDevicesPanel',
                      'openSelectedConsole', 'startCurrentSource'):
            self.assertIn(label,home)
        contract=code(ROOT/"shared/mobile-ui.json")
        for label in ('Jazyky a překlad', 'Vyber zařízení'):
            self.assertIn(label,contract)
        for label in ('PlayStation', 'Xbox', 'Spárovat TV'):
            self.assertIn(label,setup)
        self.assertIn("GameLingoUIContract.setupSteps[step]", setup)
        for label in ('addXbox', 'refreshPlayStation', 'removeConsole',
                      'removeTelevision', 'preferredConsoleID',
                      'preferredTelevisionID'):
            self.assertIn(label,devices+panel)

    def test_first_run_hides_empty_saved_device_cards(self):
        setup = code(IOS/"GameLingoSetupView.swift")
        consoles = setup.split("private var consoleStep:", 1)[1].split("private var languageStep:", 1)[0]
        self.assertIn("if !devices.consoles.isEmpty {", consoles)
        self.assertLess(consoles.index("if !devices.consoles.isEmpty"), consoles.index('GLHeader(title: "Moje konzole"'))
        self.assertNotIn('Text("Zatím žádná")', consoles)
        output = setup.split("private var outputStep:", 1)[1].split("private var readyStep:", 1)[0]
        self.assertIn("if let television = devices.chosenTelevision", output)
        self.assertIn("Text(verbatim: television.name)", output)
        self.assertNotIn('GLInterfaceText.localized("Vyber nebo přidej TV")', output)
        self.assertIn("} else {\n                    pairTVButton", output)
        self.assertIn('GLPrimaryButton(title: "Spárovat TV"', output)
        self.assertIn("showTV = true", output)

    def test_single_xbox_picker_and_no_legacy_ios_dashboard(self):
        home=code(IOS/"GameLingoHome.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        device_ui=code(IOS/"GameLingoXboxScreen.swift")
        model=code(IOS/"PlayCaptionIOSApp.swift")
        for entry in (home, setup):
            self.assertIn('GameLingoXboxScreen(devices: devices, auth: model.xbox)', entry)
            self.assertNotIn("XboxRemotePlayView", entry)
        self.assertIn("devices.addXbox(console)", device_ui)
        self.assertIn("devices.preferredConsoleID", device_ui)
        self.assertIn("XboxAccountView(auth: auth)", device_ui)
        self.assertNotIn("struct ContentView: View", model)
        self.assertNotIn("struct XboxRemotePlayView: View", model)
        self.assertNotIn("private struct PlayCaptionSection", model)
        settings=code(IOS/"SettingsHomeView.swift")
        self.assertNotIn("PlayCaptionSettingsView",settings+home)
        self.assertIn("GameLingoSettingsView",settings+home)
        self.assertIn("struct XboxAccountView: View", model)
        self.assertNotIn("struct TVSelectionView: View", model)

    def test_live_session_cannot_silently_change_selected_console(self):
        panel=code(IOS/"GameLingoDevicesPanel.swift")
        home=code(IOS/"GameLingoHome.swift")
        self.assertIn('if !model.isRunning { devices.selectConsole(console.id); cameraSource = false }', panel)
        self.assertIn('guard !model.isRunning else { pendingRemove = nil; return }', panel)
        self.assertIn('.disabled(model.isRunning)', panel)
        self.assertIn('"Nastaveno"', home)
        self.assertIn('"Snímání aktivní"', home)

    def test_one_tv_setup_persists_selected_receiver_for_translation(self):
        home=code(IOS/"GameLingoHome.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        shared=code(IOS/"GameLingoTVSetupScreen.swift")
        for entry in (home, setup):
            self.assertIn("GameLingoTVSetupScreen(devices: devices, tv: model.tv)",entry)
        self.assertIn("GameLingoTVPairingPanel(tv: tv, isRunning: model.isRunning)",shared)
        self.assertIn("tv.pairReceiver()",shared)
        self.assertIn("tv.forgetReceiver()",shared)
        self.assertIn("devices.addTelevision(",shared)
        self.assertIn('devices.selectTelevision("tv:" + address.lowercased())',shared)
        self.assertIn("devices.subtitleOutput = .googleTV",shared)
        self.assertIn("devices.prepareTV(tv)",shared)
        self.assertIn("tv.receiverPaired",shared)
        self.assertIn("tv.receiverConnected",shared)
        self.assertIn("Uložení televizoru neznamená úspěšné spárování",shared)
        self.assertNotIn('TVSelectionView(tv: model.tv)',home)
        self.assertNotIn('TVSelectionView(tv: model.tv)',setup)

    def test_playstation_terminal_session_and_delayed_callbacks_are_guarded(self):
        core = code(IOS/"PlayStation/RemotePlaySession.swift")
        ps = code(IOS/"PlayStation/GameLingoPlayStation.swift")
        wire = code(TOOLS/"wire_android_devices.py")
        self.assertIn("guard !isStopped else { return }", core)
        self.assertIn("if case .ended = state { return }", core)
        self.assertIn("case .loginPINRequired = state", core)
        self.assertIn("tp_play_session_set_login_pin(session, $0) == 0", core)
        self.assertIn(".receive(on: DispatchQueue.main)", ps)
        self.assertIn("if session.submitLoginPIN(pin)", ps)
        self.assertIn("self.onEnded = nil", ps)
        self.assertIn("val connectedSession = session ?: return", wire)
        self.assertGreaterEqual(wire.count("session === connectedSession && connectedGeneration == relaceCteni.get() && bezi"), 2)

    def test_actual_playstation_not_placeholder(self):
        ps=code(IOS/"PlayStation/GameLingoPlayStation.swift")
        core=code(IOS/"PlayStation/RemotePlaySession.swift")
        model=code(IOS/"PlayCaptionIOSApp.swift")
        native=code(IOS/"PlayStation/TPPlayCore.c")
        for label in ('RemotePlaySession(', 'onDecodedFrame:', 'tp_play_session_create'):
            self.assertIn(label,ps+core+native)
        self.assertIn('func start(_ console: RegisteredConsole)',model)
        self.assertIn('func start(_ console: XboxConsole)',model)
        self.assertIn('private func processFrame(',model)
        self.assertIn('playStation.start(console:',model)
        self.assertIn('onEnded:',model)
        self.assertIn('needsLoginPIN',ps)
        self.assertIn('submitLoginPIN(',ps)

    def test_live_connection_state_is_backed_by_real_frames(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        home=code(IOS/"GameLingoHome.swift")
        self.assertIn("model.isRunning && model.frameCount > 0",home)
        self.assertIn('model.frameCount > 0 ? GLInterfaceText.localized("Snímání aktivní") : GLInterfaceText.localized("Připojuji…")',home)
        xbox=model.split("func start(_ console: XboxConsole)",1)[1]
        xbox=xbox.split("private func processFrame",1)[0]
        self.assertIn("frameCount = 0",xbox)
        self.assertIn("guard self.sessionRevision == currentSession else { return }",xbox)
        self.assertIn("stop()\n                self.status = failure",xbox)
        ps=model.split("func start(_ console: RegisteredConsole)",1)[1]
        self.assertIn("frameCount = 0",ps)

    def test_async_ocr_tasks_cannot_leak_between_remote_play_sessions(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        options=model.split("guard let englishChoices = await",1)[1]
        options=options.split("self.lastTranslatedChoiceItems = translated",1)[0]
        self.assertIn("self.sessionRevision == currentSession",options)
        self.assertIn("defer {\n            if self.sessionRevision == currentSession",model)
        self.assertIn("self.processingChoices = false",model)
        self.assertIn("if self.sessionRevision == currentSession",model)
        stopped=model.split("func stop() {",1)[1]
        self.assertIn("processingChoices = false",stopped)
        self.assertIn("processingOCR = false",stopped)

    def test_four_regions_and_output_selection(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        edit=code(IOS/"SubtitleSettingsView.swift")
        for name in ('manualOCRBox','choicesArea','choicesOutputArea',
                     'outputFont','outputWidth','choicesOutputFont'):
            self.assertIn(name,model)
        for color in ('Modrá','Zelená','Žlutá','Oranžová'):
            self.assertIn(color,edit)
        self.assertIn('subtitleOutput == .phone',model)
        self.assertIn('model.lastChoices',code(IOS/"GameLingoHome.swift"))

    def test_subtitles_go_only_to_selected_output_and_television(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        home=code(IOS/"GameLingoHome.swift")
        relocation=model.split("func relocateActiveChoices() -> Bool", 1)[1]
        relocation=relocation.split("func stop()", 1)[0]
        self.assertIn("verifiedTelevisionOutput", relocation)
        self.assertIn("let selected = devices.chosenTelevision", model)
        self.assertIn("tv.receiverPaired && tv.receiverConnected", model)
        self.assertIn("selected.ipAddress", model)
        self.assertIn("devices.prepareTV(tv)", relocation)
        self.assertIn("tv.sendChoices(", relocation)
        self.assertIn(".onChange(of: devices.preferredTelevisionID)", home)
        self.assertIn(".onChange(of: devices.subtitleOutput)", home)
        self.assertIn("selectedTV.ipAddress", home)
        self.assertIn("previous == .googleTV && output == .phone", home)
        self.assertIn("model.tv.clearOverlays(at: previousIP)",home)
        tv=code(IOS/"PlayCaptionTV.swift")
        self.assertIn("clearOverlays(at: oldValue)",tv)
        self.assertIn("func clearOverlays(at address: String)",tv)
        self.assertIn("forceDatagram: true",tv)
        clear=tv.split("func clearOverlays(at address: String)",1)[1]
        clear=clear.split("func sendSubtitle(",1)[0]
        self.assertIn("repeatTask?.cancel()",clear)
        self.assertIn("repeatTask = nil",clear)
        self.assertIn('let choices = Data("PCV1',tv)
        self.assertIn('let dialogue = Data([',tv)
        self.assertIn("devices.prepareTV(model.tv)", home)
        self.assertIn('GameLingo iOS \\(version)', model)

    def test_both_translation_lanes_require_verified_tv_receiver(self):
        model=code(IOS/"PlayCaptionIOSApp.swift")
        self.assertIn("private var verifiedTelevisionOutput: Bool",model)
        self.assertIn("tv.receiverPaired && tv.receiverConnected",model)
        self.assertRegex(model, r"if self\.verifiedTelevisionOutput\s*\{\s*self\.tv\.sendChoices\(")
        self.assertRegex(model, r"if self\.verifiedTelevisionOutput\s*\{\s*self\.tv\.sendSubtitle\(")

    def test_android_and_google_tv_supported(self):
        manager=code(TOOLS/"GameLingoDeviceCenter.kt")
        psstore=code(TOOLS/"GameLingoPlayStationConsoles.kt")
        multi=code(TOOLS/"android_multi_xbox.py")
        patches=code(TOOLS/"wire_android_devices.py")
        tv=code(TOOLS/"polish_all_screens.py")
        receiver=code(TOOLS/"redesign_gamelingo.py")
        self.assertIn('onPlayStation',manager)
        self.assertIn('onXbox',manager)
        self.assertIn('onTV',manager)
        first_run=code(TOOLS/"GameLingoFirstRun.kt")
        self.assertIn('GameLingoFirstRun.show',patches)
        for step in ('Nastavit později', 'connectPlayStation()', 'GameLingoQRScanner.scan(activity)',
                     'GameLingoUIContract.setupSteps[step]'):
            self.assertIn(step,first_run)
        self.assertIn('Vyber zařízení',code(ROOT/"shared/mobile-ui.json"))
        self.assertIn('AES/GCM/NoPadding',psstore)
        self.assertIn('renderXboxRows()',multi)
        self.assertIn('xboxListGeneration',multi)
        self.assertIn('XboxUcet.ulozKonzoli(this, x.id, x.jmeno)',multi)
        self.assertIn('if (predvybrany >= 0)',multi)
        self.assertIn('zvolXbox(nalezene[predvybrany])',multi)
        self.assertIn('tv_dashboard',patches)
        self.assertIn('Překlad z telefonu',tv)
        self.assertIn('IPhoneTVPairing',receiver)

    def test_onboarding_completion_and_removed_device_regressions(self):
        setup=code(IOS/"GameLingoSetupView.swift")
        devices=code(IOS/"GameLingoDevices.swift")
        ps_setup=code(IOS/"PlayStation/GameLingoPlayStationSetup.swift")
        android=code(TOOLS/"GameLingoFirstRun.kt")
        for name in ("televisionPaired", "canFinish", "finishSetup()",
                     "Nastavit později", "model.playStation.registrations.$consoles"):
            self.assertIn(name,setup)
        self.assertIn("guard !hiddenConsoleIDs.contains(id)",devices)
        self.assertIn("hiddenConsoleIDs.remove(id)",devices)
        self.assertIn("placeholderID",devices)
        self.assertIn('defaults.removeObject(forKey: "tvIP")',devices)
        self.assertIn("addRegisteredPlayStation(console)",ps_setup)
        self.assertIn("Nastavit později",android)
        self.assertIn("Uložené zařízení neznamená aktivní Remote Play",android)
        self.assertIn("Theme_Material_NoActionBar_Fullscreen",android)
        self.assertIn("ViewGroup.LayoutParams.MATCH_PARENT",android)

    def test_mobile_parity_does_not_fabricate_connected_state(self):
        android=code(TOOLS/"redesign_gamelingo.py")
        first_run=code(TOOLS/"GameLingoFirstRun.kt")
        ios_setup=code(IOS/"GameLingoSetupView.swift")
        self.assertIn("2 -> consoleRegistered() && televisionPaired()", first_run)
        self.assertIn("GameLingoTVPairing.paired(activity, selected.ip)",first_run)
        self.assertIn("isEnabled = canContinue", first_run)
        self.assertIn("disabled: !canFinish", ios_setup)
        self.assertIn("Konzole je nastavená · připojení neověřeno", android)
        self.assertIn("Uložené zařízení samo o sobě nepotvrzuje živé spojení.", android)
        self.assertIn("RemotePlayService.prehledSpojeni().uroven == UrovenSpojeni.OK", android)
        self.assertIn("liveImage && tvStore.pouzeTelefon", android)
        self.assertIn("Uložený televizor nepotvrzuje živé přijetí titulků.", android)

    def test_five_destinations_native_android_navigation(self):
        nav=code(TOOLS/"GameLingoNavigation.kt")
        mobile=code(TOOLS/"redesign_gamelingo.py")
        wiring=code(TOOLS/"wire_android_devices.py")
        about=code(TOOLS/"GameLingoAbout.kt")
        contract=json.loads(code(ROOT/"shared/mobile-ui.json"))
        self.assertEqual([t["title"] for t in contract["tabs"]],
            ["Domů", "Obrazovka", "Zařízení", "Nastavení", "O aplikaci"])
        self.assertIn("GameLingoUIContract.tabs",nav)
        self.assertIn("GameLingoUIContract.tabs[rawValue]",code(IOS/"GameLingoHome.swift"))
        self.assertIn("onSettings: () -> Unit",nav)
        self.assertIn("GameLingoNavigation.create(",mobile)
        self.assertIn('onDevices = { openDevices() }',mobile)
        self.assertIn('onSettings = { openTab(3) }',mobile)
        self.assertIn('pageHost.removeAllViews()',mobile)
        self.assertIn('GameLingoDeviceCenter.content(this,',mobile)
        self.assertIn('GameLingoSettings.content(this, nast,',mobile)
        self.assertIn('fun openDevices() {',mobile)
        self.assertIn('GameLingoVisualKit.brandHeader(this)',mobile)
        self.assertIn('onAbout = { openTab(4) }',mobile)
        self.assertIn('GameLingoNavigation.kt',wiring)
        self.assertIn('GameLingoAbout.kt',wiring)
        self.assertIn("'onDevices = { openDevices() }'",wiring)
        self.assertIn("AGPL-3.0",about)

    def test_shared_mobile_contract_and_real_screens(self):
        spec=json.loads(code(ROOT/"shared/mobile-ui.json"))
        self.assertEqual(spec["setupSteps"], ["Vítej v GameLingo", "Jazyky a překlad", "Vyber zařízení"])
        self.assertEqual(len(spec["settingsSections"]), 4)
        self.assertEqual(spec["settingsSections"][-1], "OBECNÉ")
        self.assertIn("GameLingoUIContract.setupSteps[step]",
                      code(TOOLS/"GameLingoFirstRun.kt"))
        self.assertIn("GameLingoUIContract.settingsSections[3]",
                      code(TOOLS/"GameLingoSettings.kt"))
        self.assertIn("GameLingoUIContract.settingsSections[3]",
                      code(IOS/"SettingsHomeView.swift"))
        self.assertIn("GameLingoUIContract.kt",code(TOOLS/"wire_android_devices.py"))
        self.assertIn("GameLingoDiagnostics.kt",code(TOOLS/"redesign_gamelingo.py"))
        appearance=code(TOOLS/"GameLingoSubtitleAppearance.kt")
        self.assertIn("tvPismo",appearance)
        self.assertIn("tvSirka",appearance)
        self.assertIn("Propojeni.Vysilac.nastavRozvrh",appearance)
        self.assertIn("GameLingoSubtitleAppearance.show(activity)",
                      code(TOOLS/"GameLingoSettings.kt"))
        subprocess.run([sys.executable, str(TOOLS/"sync_mobile_ui.py"),
                        "--check"], cwd=ROOT, check=True, capture_output=True)

    def test_real_android_mobile_settings_are_wired_not_decorative(self):
        redesign=code(TOOLS/"redesign_gamelingo.py")
        settings=code(TOOLS/"GameLingoSettings.kt")
        nav=code(TOOLS/"GameLingoNavigation.kt")
        ocr=code(TOOLS/"GameLingoOCRConfig.kt")
        ocrScreen=code(TOOLS/"GameLingoOCRSettings.kt")
        phone=code(TOOLS/"GameLingoPhoneSubtitleStyle.kt")
        subtitle=code(TOOLS/"GameLingoSubtitleAppearance.kt")
        self.assertIn("pageHost.removeAllViews()",redesign)
        self.assertIn("GameLingoRegions.content(this,",redesign)
        self.assertIn("GameLingoDeviceCenter.content(this,",redesign)
        self.assertIn("GameLingoSettings.content(this, nast,",redesign)
        self.assertIn("GameLingoAbout.content(this)",redesign)
        self.assertIn("initialIndex = selectedMobileTab",redesign)
        self.assertIn("activeIndex = index",nav)
        self.assertIn("refresh()",nav)
        self.assertIn("GameLingoOCRSettings.show(activity, onAreas)",settings)
        self.assertIn("GameLingoOCRConfig.intervalMs()",redesign)
        self.assertIn("GameLingoOCRConfig.filterMenuText",redesign)
        self.assertIn("GameLingoPhoneSubtitleStyle.apply",redesign)
        self.assertIn("fun setFilterMenu(",ocr)
        self.assertIn("fun setExpandSearch(",ocr)
        self.assertIn("fun setMode(",ocr)
        self.assertIn("OblastTitulku.uloz(activity)",ocrScreen)
        self.assertIn("setBackground(c: Context",phone)
        self.assertIn('listOf("white", "yellow", "cyan", "green", "purple")',phone)
        self.assertIn('listOf("none", "dark", "translucent")',phone)
        self.assertIn("GameLingoPhoneSubtitleStyle.setColor(activity, choice)",subtitle)
        self.assertIn("GameLingoPhoneSubtitleStyle.setBackground(activity, choice)",subtitle)
        self.assertIn("forTelevision = true",subtitle)

    def test_tv_iphone_pairing_retries_are_identical_and_safe(self):
        receiver=code(TOOLS/"IPhoneTVPairing.kt")
        patch=code(TOOLS/"patch_tv_iphone.py")
        ios=code(IOS/"PlayCaptionTV.swift")
        native=code(TOOLS/"redesign_gamelingo.py")
        # Both clients must use one exact protocol, not two superficially
        # similar PIN screens that cannot communicate.
        self.assertIn("PCPAIR1\\tPAIR2",ios)
        self.assertIn("PCPAIR1\\\\tOK2",patch)
        self.assertIn("IPhoneTVPairing.confirmProof(c, id, casti[3], nonce, casti[5]",patch)
        self.assertIn('"PAIR" -> continue',patch)
        self.assertNotIn("PCPAIR1\\tPAIR\\t",ios)
        self.assertIn("lastSuccessPhoneId",receiver)
        self.assertIn('pairMac(saved, "OK2\\t$context")',receiver)
        self.assertNotIn("lastSuccessToken",receiver)
        self.assertIn("REPLY_RETRY_WINDOW_MS = 15_000L",receiver)
        self.assertIn("sameProof(it, proof)",receiver)
        self.assertIn("ByteArray(16).also(random::nextBytes)",receiver)
        self.assertIn('pairMac(pairingCode, "KEY2\\t$context")',receiver)
        mobile = code(TOOLS/"GameLingoTVPairing.kt")
        self.assertIn('sign(pin, "KEY2\\t$context")',mobile)
        self.assertIn("PCPAIR1\\tPAIR2",mobile)
        self.assertNotIn("PCPAIR1\\tPAIR\\t",mobile)
        self.assertIn("MessageDigest.isEqual",mobile)
        self.assertNotIn("IPhoneTVPairing.confirm(", code(TOOLS/"IPhoneTVPairingKeystoreTest.kt"))
        self.assertIn("self.pairingRetryTask = Task",ios)
        self.assertIn("self.pairingRetryTask?.cancel()",ios)
        self.assertIn("for _ in 0..<3",ios)
        self.assertIn("let request = Data(",ios)
        self.assertIn("var shownCode: String? = IPhoneTVPairing.startCode()",native)
        self.assertIn("PrijemService.statistikaPrijmu()",native)

    def test_p0_1_no_unsigned_caption_fallback(self):
        ios = code(IOS/"PlayCaptionTV.swift")
        android = code(TOOLS/"GameLingoTVPairing.kt")
        wire = code(TOOLS/"wire_android_devices.py")
        receiver = code(TOOLS/"patch_tv_iphone.py")
        tv = code(TOOLS/"GameLingoTVUi.kt")
        self.assertNotIn("sendDatagram(payload, to: target", ios)
        self.assertNotIn("sendDatagram(bytes, to: target", ios)
        self.assertIn("sendSignedSubtitle(Data(packet.utf8)", ios)
        self.assertIn('getBoolean("legacy_unsigned_receiver", false)', android)
        self.assertIn("fun signedPacket(", android)
        self.assertEqual(wire.count("GameLingoTVPairing.sendSigned("), 4)
        self.assertEqual(wire.count("GameLingoTVPairing.signedPacket(ip, data)"), 2)
        self.assertIn("GameLingoTVPairing.initialize(this)", wire)
        self.assertIn("!authenticatedGameData && !GameLingoTVPairing.legacyEnabled(c)", receiver)
        self.assertIn('p.setData(decoded.toByteArray(Charsets.UTF_8))', receiver)
        self.assertIn('listOf("PCT1", "PCV1", "PCO1", "PCQ1", "PCS1")', receiver)
        self.assertIn('casti.firstOrNull() == "PCS1" && casti.getOrNull(1) in listOf("ZADOST", "KOD")', receiver)
        self.assertIn("GameLingoTVPairing.setLegacyEnabled(a, it)", tv)

    def test_p0_2_replay_guard_and_signed_pong(self):
        ios = code(IOS/"PlayCaptionTV.swift")
        android = code(TOOLS/"GameLingoTVPairing.kt")
        receiver = code(TOOLS/"IPhoneTVPairing.kt")
        patch = code(TOOLS/"patch_tv_iphone.py")
        self.assertIn("lastWireSequence = max(lastWireSequence + 1, now * 1000)", ios)
        self.assertIn("lastWireSequence = maxOf(lastWireSequence + 1, now * 1000L)", android)
        self.assertIn('private const val FRESHNESS_MS = 120_000L', receiver)
        self.assertIn("timestamp < now - FRESHNESS_MS || timestamp > now + FRESHNESS_MS", receiver)
        self.assertIn('val slot = sequenceSlot(phoneId, lane)', receiver)
        self.assertIn("sequence <= p.getLong(slot, 0L)", receiver)
        self.assertIn("if (!p.edit().putLong(slot, sequence).commit())", receiver)
        self.assertEqual(patch.count("IPhoneTVPairing.verifyFresh(c, id,"), 3)
        self.assertIn("IPhoneTVPairing.textLane(encoded)", patch)
        self.assertIn("fields[2] == self.pendingPingNonce", ios)
        self.assertIn("self.pendingPingNonce = nil", ios)
        self.assertIn("IPhoneTVPairing.pongSignature(c, id, nonce)", patch)
        self.assertIn("MessageDigest.isEqual(", android)

    def test_p0_3_receiver_key_is_encrypted_and_migrated(self):
        receiver = code(TOOLS/"IPhoneTVPairing.kt")
        patch = code(TOOLS/"patch_tv_iphone.py")
        wire = code(TOOLS/"wire_android_devices.py")
        device_tests = code(TOOLS/"IPhoneTVPairingKeystoreTest.kt")
        self.assertIn('KeyStore.getInstance("AndroidKeyStore")', receiver)
        self.assertIn('Cipher.getInstance("AES/GCM/NoPadding")', receiver)
        self.assertIn("cipher.updateAAD(aad(phoneId))", receiver)
        self.assertIn("wrappingKey(false)", receiver)
        self.assertIn('p.edit().remove("token").remove(ENCRYPTED_TOKEN).remove("phone_id")', receiver)
        self.assertIn("putString(tokenSlot(phoneId), encrypted)", receiver)
        self.assertNotIn('putString("token", token)', receiver)
        self.assertIn("storageWriteFailed", receiver)
        self.assertIn("IPhoneTVPairing.initialize(c)", patch)
        self.assertIn("IPhoneTVPairing.initialize(this)", wire)
        self.assertIn("legacyMigrationPreservesIdentityAndReplay", device_tests)
        self.assertIn("tamperedCiphertextCannotFallBack", device_tests)
        self.assertIn("pairRetryAndClearUseEncryptedStorage", device_tests)

    def test_p0_4_independent_phone_records_and_source_isolation(self):
        receiver = code(TOOLS/"IPhoneTVPairing.kt")
        patch = code(TOOLS/"patch_tv_iphone.py")
        tv = code(TOOLS/"redesign_gamelingo.py")
        device_tests = code(TOOLS/"IPhoneTVPairingKeystoreTest.kt")
        self.assertIn('putStringSet(PHONE_IDS, ids(ctx) + phoneId)', receiver)
        self.assertIn('putStringSet(PHONE_IDS, known - phoneId)', receiver)
        self.assertIn('sequenceSlot(phoneId, lane)', receiver)
        self.assertIn('private const val SOURCE_LEASE_MS = 5000L', receiver)
        self.assertIn('IPhoneTVPairing.claimGameData(id, IPhoneTVPairing.extendsSourceLease(inner))', patch)
        self.assertIn('resetSourceIfChanged()', patch)
        self.assertIn('pribeh = newStoryReceiver()', patch)
        self.assertIn('IPhoneTVPairing.unpair(this, phone.id)', tv)
        self.assertNotIn('IPhoneTVPairing.clear(this)', tv)
        self.assertIn('twoPhonesKeepIndependentKeysReplayAndRevocation', device_tests)

    def test_p0_4_idle_packets_cannot_hold_overlay_source(self):
        receiver = code(TOOLS/"IPhoneTVPairing.kt")
        wire = code(TOOLS/"wire_android_devices.py")
        device_tests = code(TOOLS/"IPhoneTVPairingKeystoreTest.kt")
        self.assertIn("if (!extendsLease) return@synchronized", receiver)
        self.assertIn("fun stopGameData()", wire)
        self.assertIn("if (telefon) Propojeni.Vysilac.stopGameData()", wire)
        self.assertIn("live || now < upravaDo", wire)
        self.assertIn("live || now < volbyUpravaDo", wire)
        self.assertIn("emptyPacketsAndReadinessDoNotClaimSource", device_tests)
        patch = code(TOOLS/"patch_tv_iphone.py")
        self.assertIn("fun permitsStoryReadiness(phoneId: String)", receiver)
        self.assertIn("IPhoneTVPairing.permitsStoryReadiness(id)", patch)
        self.assertIn("assertEquals(revision, IPhoneTVPairing.overlayRevision())", device_tests)

    def test_p0_5_real_android_ps4_target_and_hdmi_story_contract(self):
        wire = code(TOOLS/"wire_android_devices.py")
        store = code(TOOLS/"GameLingoPlayStationConsoles.kt")
        patch = code(TOOLS/"patch_tv_iphone.py")
        verify = code(TOOLS/"verify_gamelingo.py")
        self.assertIn("Target.PS4_10", wire)
        self.assertIn("udaje.consoleTarget = udalost.host.target", wire)
        self.assertIn("ConnectInfo(ps5 = udaje.isPS5", wire)
        self.assertIn("val hevc = udaje.isPS5", wire)
        self.assertIn("it.ip == ip && it.typ == udaje.consoleType", wire)
        self.assertIn('put("target", current.consoleTarget.value)', store)
        self.assertIn("active.consoleTarget = Target.values()", store)
        self.assertIn("udaje.consoleTarget = udalost.host.target", wire)
        self.assertIn("explicit receiver mode, same as standard captions", patch)
        self.assertIn("val vstup = PrijemService.bezi ||", patch)
        self.assertIn("ConnectInfo(ps5 = udaje.isPS5", verify)

    def test_p1_1_ocr_fingerprint_is_last_successful_read_per_region(self):
        fingerprint = code(IOS/"OCRFrameFingerprint.swift")
        pipeline = code(IOS/"CaptionPipeline.swift")
        self.assertIn("CVPixelBufferLockBaseAddress(buffer, .readOnly)", fingerprint)
        self.assertIn("CVPixelBufferUnlockBaseAddress(buffer, .readOnly)", fingerprint)
        self.assertIn("now - readAt < maximumSkip", fingerprint)
        self.assertIn("lastRead = fingerprint", fingerprint)
        self.assertIn("OCRFrameGate(maximumSkip: 1.5)", pipeline)
        self.assertIn("OCRFrameGate(maximumSkip: 2.0)", pipeline)
        self.assertIn("dialogueImageGate.reset()", pipeline)
        self.assertIn("optionsImageGate.reset()", pipeline)
        # Standard dialogue and yellow lanes remain unchanged; each new
        # automatic region has a separate baseline and only updates after OCR.
        self.assertIn("automaticLanes: [UUID: AutomaticLane]", pipeline)
        self.assertIn("lane.gate.recordSuccessfulRead(fingerprint", pipeline)
        self.assertIn("lane.gate.shouldRead(fingerprint", pipeline)
        self.assertEqual(pipeline.count("recordSuccessfulRead(fingerprint"), 3)
        self.assertEqual(pipeline.count("shouldRead(fingerprint"), 3)

    def test_p1_2_latest_frame_mailboxes_are_bounded_and_session_owned(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        mailbox = code(IOS/"LatestFrameMailbox.swift")
        self.assertIn("pending = value", mailbox)
        self.assertIn("guard !draining else { return false }", mailbox)
        self.assertIn("draining = false", mailbox)
        self.assertIn("guard !closed, enabled", mailbox)
        self.assertIn("play.onFrame = receiveFrame", model)
        self.assertIn("playStation.start(console: console, onFrame: receiveFrame", model)
        self.assertIn("if dialogue.offer(timed)", model)
        self.assertIn("if options.offer(timed)", model)
        self.assertIn("while let frame = dialogue.takeNext()", model)
        self.assertIn("while let frame = options.takeNext()", model)
        self.assertIn("optionsFrames?.setEnabled(choicesEnabled && !automaticGameDialogue)", model)
        self.assertIn("LatestFrameMailbox<TimedFrame>(enabled: choicesEnabled && !automaticGameDialogue)", model)
        stop = model.split("func stop() {", 1)[1]
        self.assertIn("dialogueFrames?.close()", stop)
        self.assertIn("optionsFrames?.close()", stop)
        self.assertNotIn("await self?.processFrame(buffer", model)
        self.assertNotIn("await self?.processFrame(frame", model)

    def test_automatic_zones_use_signed_bounded_snapshots_and_old_profiles(self):
        ios = code(IOS/"PlayCaptionIOSApp.swift")
        tv = code(IOS/"PlayCaptionTV.swift")
        parser = code(TOOLS/"GameLingoZonePacket.kt")
        overlay = code(TOOLS/"GameLingoZoneOverlay.kt")
        receiver = code(TOOLS/"patch_tv_iphone.py")
        pairing = code(TOOLS/"IPhoneTVPairing.kt")
        self.assertIn("profile.automaticMode ?? false", ios)
        self.assertIn("profile.automaticZones", ios)
        self.assertIn("receiverSupportsZones", tv)
        self.assertIn("sendSignedSubtitle(Data((header + blocks)", tv)
        self.assertIn("count in 0..32", parser)
        self.assertIn("bytes.size in 1..512", parser)
        self.assertIn("isFinite()", parser)
        self.assertIn("FLAG_NOT_TOUCHABLE", overlay)
        self.assertIn("FLAG_NOT_FOCUSABLE", overlay)
        self.assertIn("ui.postDelayed(expire, 2200)", overlay)
        self.assertIn('"PCZ1", "STORY"', pairing)
        self.assertLess(receiver.index("IPhoneTVPairing.verifyFresh(c, id,"), receiver.index("ACTION_SET_ZONES"))
        self.assertIn("GameLingoZonePacket.parse(inner)", receiver)

    def test_android_automatic_is_native_and_session_bounded(self):
        wiring = code(TOOLS/"automatic_android.py")
        engine = code(TOOLS/"GameLingoAutomaticOCR.kt")
        self.assertIn("automaticXbox(f)", wiring)
        self.assertIn("automaticFrame(gl.sirka,gl.vyska)", wiring)
        self.assertIn("GameLingoAutomaticConfig.enabled(this)", wiring)
        self.assertIn("TextRecognition.getClient", engine)
        self.assertIn("busy.compareAndSet(false,true)", engine)
        self.assertIn("tracker.observe(zone.id,blocks", engine)
        self.assertIn("translating=true", engine)
        self.assertIn("GameLingoTVPairing.supportsZones(ip)", wiring)
        self.assertIn("GameLingoAutomaticConfig.profile(c)", code(TOOLS/"GameLingoProfiles.kt"))
        standalone = code(TOOLS/"GameLingoScreenTranslationService.kt")
        self.assertIn("automaticCaptureTick()", standalone)
        self.assertIn("automaticReader?.offer(image.width,image.height)", standalone)
        self.assertIn("stopAutomaticCapture()", standalone)
        self.assertIn("automaticZoneOverlay.suppress(hidden)", code(TOOLS/"direct_tv_mode.py"))

    def test_p1_3_persistent_translation_cache_uses_engine_and_language_keys(self):
        swift = code(IOS/"TranslationCache.swift")
        pipeline = code(IOS/"CaptionPipeline.swift")
        android = code(TOOLS/"GameLingoTranslationCache.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        for source in (swift, android):
            self.assertIn("20_000", source)
            self.assertIn("last_used", source)
            self.assertIn("ORDER BY last_used DESC", source)
            self.assertIn("INSERT OR REPLACE", source)
        self.assertIn("isExcludedFromBackup = true", swift)
        self.assertIn("translationCache.lookup(engine: engine", pipeline)
        self.assertIn("translationCache.remember(engine: engine", pipeline)
        self.assertNotIn("lastTranslate", pipeline)
        self.assertIn("ArrayBlockingQueue<Runnable>(128)", android)
        self.assertIn("cachedOrRun", wiring)
        self.assertIn("google(vstup, target, source)", wiring)
        self.assertIn("currentPair(source, target)", wiring)
        self.assertIn("GameLingoTranslationCacheTest.kt", wiring)

    def test_p1_4_translation_failures_never_render_original_text(self):
        pipeline = code(IOS/"CaptionPipeline.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        outcome = code(IOS/"TranslationOutcome.swift")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("async -> Result<String, GLTranslationFailure>", pipeline)
        self.assertNotIn("return original", pipeline)
        self.assertRegex(pipeline, r"case \.success\(let translated\):\s*await translationCache\.remember")
        self.assertIn("guard case .success(let rawTranslation) = outcome", model)
        self.assertIn("case .failure(let error):", model)
        self.assertIn("dialogueTranslationFailed(revision: reading.revision)", model)
        self.assertIn("optionsTranslationFailed(englishChoices", model)
        self.assertIn("dialogueRetryAt", pipeline)
        self.assertIn("optionsRetryAt", pipeline)
        self.assertIn("guard statusCode == 200", outcome)
        policy = code(TOOLS/"tv_translation_policy.py")
        self.assertIn('chyba("Online překlad není dostupný")', policy)
        self.assertIn("if (!hotovyText.isNullOrBlank())", wiring)
        self.assertIn("?.takeIf { it.isNotBlank() }", wiring)
        self.assertIn("static func edgeResponse", outcome)
        self.assertIn("text.isEmpty ? .failure(.invalidResponse)", outcome)
        self.assertIn("vysledky[i] = null", wiring)
        self.assertIn("prelozene.size != volby.size", wiring)

    def test_p1_5_stabilization_and_bounded_dialogue_display(self):
        pipeline = code(IOS/"CaptionPipeline.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        stability = code(IOS/"CaptionStability.swift")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("dialogueStability.needsConfirmation", pipeline)
        self.assertIn("gameTextStability.needsConfirmation", pipeline)
        self.assertIn('RecognizedCaption(english: "", revision: revision', pipeline)
        self.assertIn("now - since >= 0.8", stability)
        self.assertIn("text.split(separator:", stability)
        self.assertIn("min(10.0, max(3.0, requested))", model)
        empty_read = model.split("if reading.english.isEmpty {", 1)[1].split('self.write("OCR:', 1)[0]
        self.assertIn("self.hideAfterSourceGone(currentSession: currentSession)", empty_read)
        self.assertIn("return", empty_read)
        self.assertIn("self.dialogueDisplayRevision == display", model)
        self.assertIn("self.clearDialogueOutput()", model)
        self.assertIn("ukaz(cesky, 4000, box, radku, barvyJmen)", wiring)
        self.assertIn("ukazanoKdy = android.os.SystemClock.elapsedRealtime()", wiring)
        self.assertIn("(ukazanoKdy + 4000L - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)", wiring)
        self.assertNotIn("coerceIn(0L, 800L)", wiring)
        direct = code(TOOLS/"direct_tv_mode.py")
        self.assertIn("GameLingoOCRConfig.displayMs(this,4000L) - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)", direct)

    def test_point1_standalone_empty_ocr_preserves_reading_deadline(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        blank = capture.split("if (value.isBlank()) {", 1)[1].split("blankFrames = 0;", 1)[0]
        self.assertIn('epoch++; candidate = ""', blank)
        self.assertNotIn("shownUntil =", blank)
        self.assertNotIn('render("")', blank)
        self.assertIn("else { hideAfterSourceGone(); resetText(preserveDisplay = true) }", capture)
        reset = capture.split("private fun resetText(", 1)[1].split("private fun setSource", 1)[0]
        self.assertIn("preserveDisplay: Boolean = false", reset)
        self.assertIn('if (!preserveDisplay) {\n            translatedText = ""; shownAt = 0L; shownUntil = 0L', reset)
        self.assertIn('cancelSample(); resetText(); clearYellow(); clearHint(); render("")', capture)
        self.assertIn('paused = !paused; stopAutomaticCapture(); cancelSample(); resetText(); render("")', capture)
        self.assertIn('shownUntil = 0L; translatedText = ""; render("")', capture)
        self.assertIn('if (wasActive) { render(""); clearYellow(); clearHint() }', capture)
    def test_point2_dialogue_replacement_preserves_old_deadline_until_success(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        accept = capture.split("private fun accept(raw: String)", 1)[1].split("private fun translateLatest()", 1)[0]
        change = accept.rsplit("if (value != candidate) {", 1)[1]
        self.assertIn('if (shown != value) shown = ""', change)
        self.assertNotIn('render("")', change)
        self.assertNotIn("shownUntil =", change)
        translate = capture.split("private fun translateLatest()", 1)[1].split("private fun clearHint()", 1)[0]
        failure = translate.split("} else if (current) {", 1)[1].split("} else {", 1)[0]
        self.assertNotIn('render("")', failure)
        self.assertNotIn("shownUntil =", failure)
        self.assertIn("shownUntil = shownAt + duration", translate)
        self.assertIn("render(displayText, duration)", translate)
        self.assertIn("dialogueWindow.render(value, corrected", translate)
        model = code(IOS/"PlayCaptionIOSApp.swift")
        failure = model.split("guard case .success(let rawTranslation) = outcome else {", 1)[1].split("let czech =", 1)[0]
        self.assertNotIn("clearDialogueOutput()", failure)
        self.assertNotIn("scheduleDialogueExpiry", failure)
        self.assertIn("dialogueTranslationFailed", failure)
        self.assertIn("self.scheduleDialogueExpiry(seconds: displaySeconds", model)

    def test_point3_standalone_reuses_console_dialogue_stabilization(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        self.assertIn("private val dialogueStability = StabilitaTextu()", capture)
        reset = capture.split("private fun resetText(", 1)[1].split("private fun setSource", 1)[0]
        self.assertIn("dialogueStability.reset()", reset)
        accept = capture.split("private fun accept(raw: String)", 1)[1].split("private fun translateLatest()", 1)[0]
        self.assertIn("else { dialogueStability.reset(); hideAfterSourceGone() }", accept)
        # Ordinary dialogue only: story still owns its original stabilization.
        gate = "if (!dialogueStability.prijmi(value, radku = currentRows)) return"
        self.assertIn(gate, accept)
        self.assertLess(accept.index("story.prijmiOcr"), accept.index(gate))
        self.assertLess(accept.index(gate), accept.rindex("candidate = value"))
        self.assertNotIn("StabilitaTextu.opakovaneCteni", accept)

    def test_point4_dialogue_deduplicates_only_equivalent_whitespace(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        accept = capture.split("private fun accept(raw: String)", 1)[1].split("private fun translateLatest()", 1)[0]
        self.assertIn("else GameLingoDialogueText.normalize(raw)", accept)
        self.assertLess(accept.index("GameLingoDialogueText.normalize(raw)"), accept.index("if (value != candidate)"))
        self.assertIn("if (value == shown) return", accept)
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn('folder/"GameLingoDialogueText.kt"', wiring)
        self.assertIn('unit_dir/"GameLingoDialogueTextTest.kt"', wiring)

    def test_point5_standalone_aba_generations_guard_delayed_results(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        accept = capture.split("private fun accept(raw: String)", 1)[1].split("private fun translateLatest()", 1)[0]
        change = accept.rsplit("if (value != candidate) {", 1)[1]
        self.assertLess(change.index("epoch++"), change.index("candidate = value"))
        self.assertIn('lastAttempt = ""; lastAttemptAt = 0L', change)
        self.assertNotIn("shownUntil =", change)
        dialogue = capture.split("private fun translateLatest()", 1)[1].split("private fun clearHint()", 1)[0]
        self.assertIn("ticket == epoch && value == candidate", dialogue)
        self.assertIn("ticket != epoch) translateLatest()", dialogue)
        self.assertNotIn("candidate != value) translateLatest()", dialogue)
        yellow = capture.split("private fun processYellow(", 1)[1].split("private fun yellowHighlight", 1)[0]
        change = yellow.split("if (words != yellowCandidate) {", 1)[1]
        self.assertLess(change.index("yellowEpoch++"), change.index("yellowCandidate = words"))
        self.assertIn("yellowLastAttempt = emptyList(); yellowAttemptAt = 0L", change)
        yellow_translate = capture.split("private fun translateYellow()", 1)[1].split("private fun render(", 1)[0]
        self.assertIn("ticket == yellowEpoch && value == yellowCandidate", yellow_translate)
        self.assertIn("ticket != yellowEpoch) translateYellow()", yellow_translate)
        self.assertIn('hintText=text; hintEpoch++; hintAttempt=""; hintAttemptAt=0L', capture)

    def test_point6_standalone_dialogue_priority_keeps_single_reader(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        self.assertIn("GameLingoOCRCadence.standaloneRegion(blueEnabled, yellowReady,", capture)
        self.assertIn("yellowEnabled && GameLingoOCRConfig.standaloneYellowReady", capture)
        self.assertIn("if (blueEnabled || yellowReady)", capture)
        self.assertIn("!ocrBusy && !capturePending && wantFrame.get() == 0", capture)
        self.assertNotIn("nextYellow", capture)
        self.assertIn("blueSinceYellow >= 2", code(TOOLS/"GameLingoOCRCadence.kt"))

    def test_point7_standalone_frame_gate_is_bounded_and_requires_settled_text(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        self.assertIn("GameLingoFrameGate(650L)", capture)
        self.assertIn("GameLingoFrameGate(850L)", capture)
        self.assertIn("GameLingoFrameGate.mix(fingerprint, pixel)", capture)
        self.assertIn("!storyMode && !hintsEnabled && !diagnostic && blankFrames == 0", capture)
        self.assertIn("!translationBusy && candidate.isNotBlank() && candidate == shown", capture)
        self.assertIn("blueFrameGate.record(fingerprint, ocrStarted)", capture)
        self.assertIn("yellowFrameGate.record(fingerprint, started)", capture)
        self.assertIn("blueFrameGate.reset()", capture)
        self.assertIn("yellowFrameGate.reset()", capture)
        self.assertIn("unit_dir/\"GameLingoFrameGateTest.kt\"", code(TOOLS/"wire_gamelingo_languages.py"))

    def test_point8_standalone_admission_yields_yellow_between_items(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        self.assertIn("if (!storyMode && (yellowRequest || hintBusy)) return", capture)
        self.assertIn("if (!storyMode && (translationBusy || hintBusy || dialogueReady(now)))", capture)
        self.assertIn("if (!storyMode && (translationBusy || yellowRequest || dialogueReady(now)))", capture)
        self.assertIn("if (!storyMode && dialogueReady(SystemClock.elapsedRealtime()))", capture)
        self.assertIn("yellowPartial.clear(); yellowPartial.addAll(results)", capture)
        self.assertIn("if (yellowPartialEpoch != ticket)", capture)
        self.assertIn("next(results.size)", capture)
        self.assertIn("if (!storyMode && !translationBusy) translateYellow()", capture)
        self.assertIn("yellowShown.isNotEmpty() || value.isEmpty()", capture)

    def test_point9_tv_overlay_reuses_unchanged_choice_views(self):
        patch = code(TOOLS/"patch_tv_iphone.py")
        point = patch.split("# Point 9:", 1)[1]
        self.assertIn("ChoiceRenderKey(volby.toList(), o.toList(), vystup?.toList()", point)
        self.assertIn("GameLingoTVCaptionStyle.background(this)", point)
        self.assertIn("lastChoiceRender == renderKey && seznam.isAttachedToWindow", point)
        self.assertIn("seznam.childCount == volby.size", point)
        self.assertIn("listOf(lastChoiceSelected, vybrana).distinct()", point)
        self.assertIn("lastChoiceRender = null", point)
        self.assertIn("lastChoiceRender = renderKey", point)
        self.assertIn("if (text !is String || this.text !is String || this.text != text)", point)

    def test_point10_pipeline_metrics_are_measured_at_real_boundaries(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        for marker in ("postedAt - cropStarted", "SystemClock.elapsedRealtime() - postedAt",
                       "phases.record(\"Dialog OCR\", lastOcrMs)",
                       "phases.record(\"Dialog čekání po OCR\", now - started)",
                       "phases.record(\"Dialog od přijetí textu k předání\"", "currentPhases = phases"):
            self.assertIn(marker, capture)
        self.assertIn("frame.receivedAt", model)
        self.assertIn("reading.ocrMilliseconds", model)
        self.assertIn("GameLingoNetworkPerformance.waitMs(waited)", code(TOOLS/"translation_backpressure.py"))
        self.assertIn("GameLingoNetworkPerformance.summary()", code(TOOLS/"GameLingoDiagnostics.kt"))

    def test_point11_xbox_metrics_precede_ocr_sampling(self):
        xbox = code(IOS/"XboxRemotePlay.swift")
        self.assertLess(xbox.index("metrics.record(width: Int(frame.width)"), xbox.index("guard now - lastSent > minimumFrameInterval"))
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("videoMetrics.frame(sirka, vyska, android.os.SystemClock.elapsedRealtime())", wiring)
        self.assertIn("GameLingoXboxDiagnostics.begin(videoMetrics)", wiring)
        self.assertIn("videoMetrics.stop()", wiring)
        self.assertIn("remotePlay.streamSummary()", code(IOS/"PlayCaptionIOSApp.swift"))

    def test_point12_native_webrtc_stats_use_video_counters_and_selected_pair(self):
        xbox = code(IOS/"XboxRemotePlay.swift")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        for marker in ("inbound-rtp", "bytesReceived", "packetsReceived", "packetsLost",
                       "jitter", "selectedCandidatePairId", "currentRoundTripTime"):
            self.assertIn(marker, xbox)
            self.assertIn(marker, wiring)
        self.assertIn("peer.statistics(completionHandler:", xbox)
        self.assertIn("peer.getStats", wiring)
        self.assertIn("statsPending", xbox)
        self.assertIn("statsPending", wiring)
        self.assertIn("h.postDelayed(poll, 5000L)", wiring)
        self.assertIn("old.id == sample.id", code(IOS/"PerformanceWindow.swift"))
        self.assertIn("old.id != sample.id", code(TOOLS/"GameLingoPerformance.kt"))

    def test_point13_capture_watchdog_never_releases_busy_ocr(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        tick = capture.split("private val tick = object : Runnable", 1)[1].split("override fun onStartCommand", 1)[0]
        self.assertIn("GameLingoCaptureWatchdog.state(now - frameAt, ocrBusy", tick)
        self.assertIn("now - sampleRequestedAt >= 1500L", tick)
        self.assertNotIn("ocrBusy = false", tick)
        self.assertIn("lastFrameReceivedAt.set(SystemClock.elapsedRealtime())", capture)
        self.assertIn("ocrInFlightAt = ocrStarted", capture)
        self.assertIn("ocrInFlightAt = started", capture)
        self.assertIn("watchdogState", capture)
        self.assertIn("processingOCR && now - ocrStartedAt >= 5", code(IOS/"PlayCaptionIOSApp.swift"))

    def test_point14_xbox_reconnect_is_bounded_same_console_and_cancellable(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        xbox = code(IOS/"XboxRemotePlay.swift")
        self.assertIn("guard xboxReconnectAttempts < 2", model)
        self.assertIn("self.sessionRevision == waitingRevision", model)
        self.assertIn("self.startXbox(console)", model)
        self.assertIn("self.remotePlay === play", model)
        self.assertIn("xboxReconnectTask?.cancel()", model.split("func stop() {", 1)[1])
        self.assertIn("guard markEstablished() else { return }", xbox)
        self.assertIn("!retry || established", xbox)
        self.assertIn("if age >= 30", xbox)
        self.assertIn('signalInterruption("Xbox ukončil vzdálené hraní", retry: false)', xbox)
        self.assertIn("interruptionSent = true", xbox)

    def test_xbox_recovery_respects_terminal_stop_and_current_generation(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        recovery = code(TOOLS/"xbox_recovery.py")
        self.assertIn("if xboxReconnectAttempts > 0,", model)
        self.assertIn("Self.isTransientXboxRecoveryError(networkError.code)", model)
        policy = model.split("private static func isTransientXboxRecoveryError", 1)[1].split("/// Retry", 1)[0]
        self.assertIn(".networkConnectionLost", policy)
        self.assertIn("default:\n            return false", policy)
        self.assertIn("xboxGeneration != relaceCteni.get()", recovery)
        self.assertIn("xboxGeneration == relaceCteni.get()", recovery)
        self.assertIn("automat = false", recovery)
        self.assertIn("ui.removeCallbacks(hlidacSpojeni)", recovery)
        self.assertIn("stopSelf()", recovery)
        self.assertIn("s = protect_xbox_recovery(s)", code(TOOLS/"wire_gamelingo_languages.py"))

    def test_point15_receiver_reconnect_keeps_auth_and_session_generation(self):
        ios = code(IOS/"PlayCaptionTV.swift")
        self.assertIn("self.receiverGeneration == generation", ios)
        self.assertIn("receiverRetrySeconds = min(30, delay * 2)", ios)
        self.assertIn("self.scheduleReceiverReconnect(conn, target: target)", ios)
        self.assertIn("ReceiverSecretStore.load(target) != nil", ios)
        self.assertIn("fields[3] == expected", ios)
        self.assertIn("self.receiverRetrySeconds = 3", ios)
        android = code(TOOLS/"GameLingoTVPairing.kt")
        self.assertIn("Propojeni.Vysilac.outputGeneration() != generation", android)
        self.assertIn("MessageDigest.isEqual(", android)
        self.assertIn("reply.port == port", android)
        self.assertIn("checkedPortAt = 0L", android)
        self.assertIn("if (live) GameLingoTVPairing.verifyActive(ip, relace)", code(TOOLS/"wire_android_devices.py"))

    def test_p1_6_translation_breakers_and_fallback(self):
        pipeline = code(IOS/"CaptionPipeline.swift")
        swift = code(IOS/"TranslationCircuitBreaker.swift")
        kotlin = code(TOOLS/"GameLingoCircuitBreaker.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("failures >= 2", swift)
        self.assertIn("now + 30", swift)
        self.assertIn("!probing", swift)
        self.assertIn("ticket.generation == generation", swift)
        self.assertIn("failures >= 2", kotlin)
        self.assertIn("now + 30_000L", kotlin)
        self.assertIn("@Synchronized fun acquire", kotlin)
        self.assertIn('["google-gtx-v1", "microsoft-edge-v1", "mymemory-v1"]', pipeline)
        self.assertIn("GLTranslationOutcome.edgeResponse", pipeline)
        self.assertIn("requestEdge(original:", pipeline)
        self.assertIn("providerBreakers[engine", pipeline)
        self.assertIn("GLTranslationOutcome.myMemoryResponse", pipeline)
        self.assertIn("guardedGoogle(t, jazyk, source)", wiring)
        self.assertIn("guardedProvider(edgeBreaker)", wiring)
        self.assertIn("guardedProvider(myMemoryBreaker)", wiring)
        self.assertIn("GameLingoCircuitBreakerTest.kt", wiring)

    def test_p1_7_local_performance_diagnostics_are_bounded(self):
        swift = code(IOS/"PerformanceWindow.swift")
        device = code(IOS/"LocalDeviceDiagnostics.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        android = code(TOOLS/"GameLingoPerformance.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("capacity: Int = 128", swift)
        self.assertIn("samples[cursor] = value", swift)
        self.assertIn("0.95", swift)
        self.assertIn("MACH_TASK_BASIC_INFO", device)
        self.assertIn("thermalState", device)
        self.assertIn("beginPerformanceSession(currentSession:", model)
        self.assertIn("recordPerformance", model)
        self.assertIn("removeObject(forKey: sessionMarker)", model)
        self.assertIn("Debug.getPss()", android)
        self.assertIn("currentThermalStatus", android)
        self.assertIn("session_open", android)
        self.assertIn("performance.begin(this)", wiring)
        self.assertIn("performance.end(this)", wiring)
        self.assertIn("performance.record(perfToken", wiring)

    def test_p1_8_modes_use_independent_adaptive_game_text_cadence(self):
        pipeline = code(IOS/"CaptionPipeline.swift")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        config = code(TOOLS/"GameLingoOCRConfig.kt")
        cadence = code(TOOLS/"GameLingoOCRCadence.kt")
        redesign = code(TOOLS/"redesign_gamelingo.py")
        tv = code(TOOLS/"GameLingoTVUi.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("mode.cadence.gameTextInterval", pipeline)
        self.assertNotIn("timeIntervalSince(lastOptionsRead) > 1.0", pipeline)
        self.assertIn("optionsRecentCost", pipeline)
        self.assertIn("gameLanguage: activeGameLanguage, mode: self.ocrMode", model)
        self.assertIn("tvIntervalMs(lowPower: Boolean)", config)
        self.assertIn("GameLingoOCRConfig.tvIntervalMs(uspora)", redesign)
        self.assertIn('if (lowPower && !phone) return 600L', cadence)
        self.assertIn("GameLingoOCRConfig.setMode(a, keys[index])", tv)
        self.assertIn("GameLingoOCRConfig.gameTextIntervalMs", wiring)
        self.assertIn("gameTextOcrCostMs", wiring)

    def test_p1_9_translation_generation_invalidates_old_results(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        devices = code(IOS/"GameLingoDevices.swift")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        languages = code(TOOLS/"GameLingoLanguages.kt")
        self.assertIn("dialogueRevision.accepts(readingRevision)", model)
        self.assertIn("optionsRevision.accepts(readingRevision)", model)
        self.assertIn("optionsTranslation?.cancel()", model)
        self.assertIn("dialogueTranslation?.cancel()", model)
        self.assertIn("devices.onOutputChanged", model)
        self.assertIn("onOutputChanged?()", devices)
        self.assertIn("GameTextKey(context.source, context.target, text)", wiring)
        self.assertIn("currentTranslation(context)", wiring)
        self.assertIn("generation != gameTextGeneration", wiring)
        self.assertIn("changes.incrementAndGet()", languages)

    def test_p2_1_qr_pairing_uses_existing_confirmation_and_pin(self):
        tv = code(TOOLS/"GameLingoQRDisplay.kt")
        parser = code(TOOLS/"GameLingoPairingQR.kt")
        scanner = code(TOOLS/"GameLingoQRScanner.kt")
        center = code(TOOLS/"GameLingoDeviceCenter.kt")
        ios = code(IOS/"GameLingoTVSetupScreen.swift")
        self.assertIn("GLPAIR2", parser)
        self.assertIn("IPhoneTVPairing.activeCode() != pin", tv)
        self.assertIn("handler.removeCallbacks(tick)", tv)
        self.assertIn("GameLingoPairingQR.parse(it)", scanner)
        self.assertIn("GameLingoTVPairing.show(c, candidate, initialPin = qr.pin)", center)
        self.assertIn("tv.receiverCode = code.pin", ios)
        self.assertIn("tv.pairReceiver()", ios)
        self.assertNotIn("pairReceiver()", ios.split("GameLingoQRScanner(onCode:", 1)[1])

    def test_p2_3_history_is_opt_in_bounded_and_separate(self):
        swift = code(IOS/"TranslationHistory.swift")
        kotlin = code(TOOLS/"GameLingoTranslationHistory.kt")
        model = code(IOS/"PlayCaptionIOSApp.swift")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("enabled = false", swift)
        self.assertIn("limit = 200", swift)
        self.assertIn("if !value { clear() }", swift)
        self.assertIn("recording = false", kotlin)
        self.assertIn("LIMIT = 200", kotlin)
        self.assertIn("if (!value) entries.clear()", kotlin)
        self.assertIn("self.translationHistory.append", model)
        self.assertIn("if (dobry) GameLingoTranslationHistory.append", wiring)
        self.assertIn("GameLingoTranslationHistory.append", wiring)
        self.assertIn("GameLingoHistoryUi.show", code(TOOLS/"GameLingoSettings.kt"))
        self.assertIn("GameLingoHistoryUi.show", code(TOOLS/"GameLingoTVUi.kt"))

    def test_p2_4_glossary_is_profile_local_and_post_translation(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        profile = code(IOS/"SettingsHomeView.swift")
        store = code(TOOLS/"GameLingoGlossaryStore.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("activeGlossaryProfileID", model)
        self.assertIn("var glossary: GameGlossary? = nil", profile)
        self.assertIn("glossaryTranslation(original:", model)
        self.assertIn("GameLingoGlossaryStore.generation()", wiring)
        self.assertIn("GameLingoGlossaryStore.apply", wiring)
        self.assertIn('gamelingo_glossary_v1', store)
        self.assertIn("GameLingoGlossaryStore.activate(activity, name)", code(TOOLS/"GameLingoProfiles.kt"))

    def test_tv_translation_sdk_is_removed_at_build_and_binary_levels(self):
        policy = code(TOOLS/"tv_translation_policy.py")
        brand = code(TOOLS/"brand_android.py")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        verify = code(TOOLS/"verify_gamelingo.py")
        workflow = code(ROOT/".github/workflows/audit-0.5-ci.yml")
        self.assertIn("strip_dependency(gradle.read_text", brand)
        self.assertIn("t = tv_translator(t)", wiring)
        self.assertIn("finalize_tv(root, folder)", wiring)
        self.assertIn("assert_tv_source(root)", verify)
        self.assertIn("resolutionStrategy.eachDependency", policy)
        self.assertIn("skipGoogle = true", wiring)
        self.assertIn("skipGoogle: Boolean = false", policy)
        self.assertIn("Rychlý náhled není v paměti", policy)
        self.assertIn('verify_tv_translation_apk.py tv "$APK"', workflow)
        self.assertIn('verify_tv_translation_apk.py mobile "$APK"', workflow)

    def test_translation_sdk_apk_gate_detects_multidex_references(self):
        import tempfile
        import zipfile
        import importlib.util
        spec = importlib.util.spec_from_file_location("apk_policy", TOOLS/"verify_tv_translation_apk.py")
        gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(gate)
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp)/"candidate.apk"
            def make(*parts):
                with zipfile.ZipFile(apk, "w") as out:
                    for i, part in enumerate(parts):
                        out.writestr("classes.dex" if i == 0 else f"classes{i+1}.dex", b"dex\n035\x00"+part)
            make(b"Lcom/google/mlkit/vision/text/TextRecognition;")
            gate.verify(apk, "tv")
            with self.assertRaises(AssertionError):
                gate.verify(apk, "mobile")
            for marker in gate.MARKERS:
                make(b"normal", marker)
                with self.assertRaises(AssertionError):
                    gate.verify(apk, "tv")
            make(b"normal", gate.MOBILE_MARKER)
            gate.verify(apk, "mobile")
            with self.assertRaises(AssertionError):
                gate.verify(apk, "tv")

    def test_mobile_offline_isolated_and_preserves_privacy_contract(self):
        mobile = code(TOOLS/"mobile_offline_translation.py")
        helper = code(TOOLS/"GameLingoMobileOffline.kt")
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        brand = code(TOOLS/"brand_android.py")
        self.assertIn('if not tv_variant(root):\n        t = install_mobile_translator(t, folder)', wiring)
        self.assertLess(wiring.index("t = protect_cache_writes(t)"), wiring.index("t = install_mobile_translator(t, folder)"))
        self.assertIn('        gradle.write_text(strip_dependency', brand)
        self.assertIn('remember("mlkit-v1", source, target, vstup, result, cacheRevision)', mobile)
        self.assertIn("GameLingoLanguages.revision != languageRevision", mobile)
        self.assertIn("offline.close()", mobile)
        self.assertIn("requireWifi()", helper)
        self.assertIn("pending >= MAX_PENDING", helper)
        self.assertIn("UI_MODE_TYPE_TELEVISION", helper)
        self.assertIn("FEATURE_LEANBACK", helper)
        self.assertIn("active !== client", helper)
        self.assertIn("translator.close()", helper)

    def test_tv_automatic_udp_port_fallback_is_coordinated(self):
        """Do not move TV port without moving iOS/Android pairing and captions."""
        tv_patch=code(TOOLS/"patch_tv_iphone.py")
        ios=code(IOS/"PlayCaptionTV.swift")
        android=code(TOOLS/"GameLingoTVPairing.kt")
        tv_screen=code(TOOLS/"redesign_gamelingo.py")
        self.assertIn("listOf(PORT_TITULKY, 41990, 41991, 41992, 41993)",tv_patch)
        self.assertIn("reuseAddress = false",tv_patch)
        self.assertIn("firstNotNullOfOrNull",tv_patch)
        self.assertIn('casti[0] == "GLPORT1"',tv_patch)
        self.assertIn('GLPORT1\\\\tPORT\\\\t$prijimaciPort',tv_patch)
        self.assertIn("fun prijimaciPort()",tv_patch)
        self.assertIn('PrijemService.prijimaciPort()',tv_screen)
        self.assertIn("private func discoverReceiverPort(",ios)
        self.assertIn('GLPORT1\\tQUERY',ios)
        self.assertIn("for candidate in 41989...41993",ios)
        self.assertIn("self.beginReceiverPairing(target: target, code: code, port: port)",ios)
        self.assertIn("self.connectLiveReceiver(target: target, port: port)",ios)
        self.assertIn("UInt16(cachedReceiverPort(target))",ios)
        self.assertIn("private fun receiverPort(ip: String)",android)
        self.assertIn("receiverPort(tv.ip)",android)
        self.assertIn("receiverPort(ip)",android)
        # The native Android caption sender must use the same resolved port,
        # not merely the PIN handshake and PING.
        sender=code(TOOLS/"wire_android_devices.py")
        for call in ('GameLingoTVPairing.portFor(cil.first)',
                     'GameLingoTVPairing.portFor(ip)'):
            self.assertIn(call, sender)
        self.assertIn("fun portFor(ip: String): Int", android)
        self.assertIn("checkedPortAt", android)
        self.assertIn('sender_marker = "    object Vysilac {"', sender)
        self.assertIn("sender = one(sender, old, new, label)", sender)
        self.assertIn("transport.write_text(prefix + sender_marker + sender", sender)

    def test_tv_style_control_and_live_game_profile_save(self):
        """TV style is authenticated and persisted; running games may be saved."""
        ios=code(IOS/"PlayCaptionTV.swift")
        model=code(IOS/"PlayCaptionIOSApp.swift")
        settings=code(IOS/"SettingsHomeView.swift")
        regions=code(IOS/"SubtitleSettingsView.swift")
        patch=code(TOOLS/"patch_tv_iphone.py")
        style=code(TOOLS/"GameLingoTVCaptionStyle.kt")
        self.assertIn("func updateCaptionStyle(color: String, background: String)", ios)
        self.assertIn("let body = \"STYLE", ios)
        self.assertIn("self.updateCaptionStyle(", ios)
        self.assertIn("tvTextColor: tvTextColor.rawValue",model)
        self.assertIn("tvBackground: tvBackground.rawValue",model)
        self.assertIn("if let name = profile.tvTextColor",model)
        self.assertIn("if let name = profile.tvBackground",model)
        self.assertIn('Text("Barva textu na TV")',settings)
        self.assertIn('Picker("Pozadí na TV"',settings)
        self.assertIn('savedProfileMessage',settings)
        self.assertNotIn('disabled: profileName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty\n                        || model.isRunning',settings)
        self.assertIn("if model.isRunning && model.relocateActiveChoices()",regions)
        self.assertIn("GameLingoTVCaptionStyle.kt",patch)
        self.assertIn('"STYLE" -> {',patch)
        self.assertIn("GameLingoTVCaptionStyle.save(c, textColor, background)",patch)
        self.assertIn("PixelFormat.TRANSLUCENT",patch)
        self.assertIn("pod.visibility = if (GameLingoTVCaptionStyle.isDark(this))",patch)
        for item in ('"white"', '"yellow"', '"cyan"', '"green"', '"purple"',
                     '"none"', '"dark"', '"translucent"'):
            self.assertIn(item,style)

    def test_ios_update_ui_localization(self):
        import re
        updater = code(IOS/"GameLingoUpdates.swift")
        keys = re.findall(r'(?:UpdateFailure|Button|Label|GLInterfaceText.localized)\("([^"]+)"', updater)
        keys += re.findall(r'(?:message|messageDetail) = "([^"]+)"', updater)
        keys += ["Novější verze není dostupná.", "Je dostupná nová verze.", "Stahování zrušeno.", "Stažení se nezdařilo:", "Neznámá chyba.", "Aktualizace", "Nepodepsaná IPA: instalaci a podpis provádíš vlastním nástrojem."]
        for lang in ("en", "sk"):
            catalog = code(IOS/(lang + ".lproj")/"Localizable.strings")
            for key in set(keys):
                if key:
                    self.assertIn(json.dumps(key, ensure_ascii=False) + " = ", catalog)
        self.assertIn("GLInterfaceText.localized(updates.message)", updater)
        self.assertIn("GLInterfaceText.localized($0)", updater)

    def test_android_tv_update_ui_localization(self):
        import re
        updater = code(TOOLS/"GameLingoTVUpdater.kt")
        labels = code(TOOLS/"GameLingoLanguages.kt")
        keys = re.findall(r'tr\(a, "([^"]+)"\)', updater)
        self.assertGreaterEqual(len(set(keys)), 20)
        for key in set(keys):
            self.assertIn(key, json.loads(code(ROOT/"shared/locales/cs.json")))
        for script in ("wire_mobile_update.py", "direct_tv_mode.py"):
            self.assertIn('GameLingoLanguages.ui(activity, "Zkontrolovat aktualizace")', code(TOOLS/script))

    def test_tv_updater_requires_signed_newer_apk_and_system_consent(self):
        updater = code(TOOLS/"GameLingoTVUpdater.kt")
        wiring = code(TOOLS/"direct_tv_mode.py")
        publisher = code(ROOT/".github/workflows/publish-gamelingo-current.yml")
        for marker in ("code(archive) > code(current)", "archive.packageName == ID",
                       "signers(archive) == setOf(next.cert)", "signers(current) == setOf(next.cert)",
                       "hex(digest.digest()) == next.hash", "canRequestPackageInstalls()",
                       "FLAG_GRANT_READ_URI_PERMISSION", "MAX_APK", "instanceFollowRedirects = false"):
            self.assertIn(marker, updater)
        for marker in ("GameLingoTVUpdater.checkAtStartup", "GameLingoTVUpdater.show",
                       "REQUEST_INSTALL_PACKAGES", "gamelingo-updates/", 'android:exported="false"'):
            self.assertIn(marker, wiring)
        self.assertIn("create_tv_update_manifest.py", publisher)
        self.assertIn("GameLingo-TV-update.json", publisher)
        self.assertIn("GameLingoTVUpdater", code(TOOLS/"verify_tv_ui_apk.py"))

    def test_tv_update_manifest_rejects_wrong_cert_and_package(self):
        import importlib.util
        import tempfile
        spec = importlib.util.spec_from_file_location("tv_update_metadata", TOOLS/"create_tv_update_manifest.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory)/"GameLingo-GoogleTV-v1.2.3-dev-TEST.apk"
            apk.write_bytes(b"test fixture only")
            tag = "v1.2.3-test-abcdef12-r123"
            badging = "package: name='cz.caseycz.gamelingo.tv' versionCode='123' versionName='1.2.3-dev.123-abcdef12'"
            cert = "a"*64
            info = module.metadata(apk,tag,"1.2.3",badging,cert,cert)
            self.assertEqual(info["versionCode"],123)
            self.assertEqual(info["size"],len(b"test fixture only"))
            with self.assertRaises(ValueError):
                module.metadata(apk,tag,"1.2.3",badging,cert,"b"*64)
            with self.assertRaises(ValueError):
                module.metadata(apk,tag,"1.2.3",badging.replace(".tv",".mobile"),cert,cert)

    def test_publisher_verifies_draft_before_exposing_and_cleanup(self):
        workflow = code(ROOT/".github/workflows/publish-gamelingo-current.yml")
        self.assertIn("--draft --prerelease", workflow)
        self.assertIn('gh release upload "$TAG" "release/$name"', workflow)
        self.assertLess(workflow.index('cmp "release/$name" "published/$name"'),
                        workflow.index('gh release edit "$TAG"'))
        self.assertLess(workflow.index('gh release edit "$TAG"'),
                        workflow.index('gh release delete "$old"'))

    def test_release_tag_cleanup_is_conservative_and_preserves_current(self):
        import runpy
        plan = runpy.run_path(str(TOOLS/"cleanup_release_tags.py"))["plan_cleanup"]
        names = ("GameLingo-iOS-v0.5.6-dev-unsigned.ipa",
                 "GameLingo-Android-v0.5.6-dev-TEST.apk",
                 "GameLingo-GoogleTV-v0.5.6-dev-TEST.apk", "SHA256SUMS.txt",
                 "GameLingo-TV-update.json", "GameLingo-Android-update.json",
                 "GameLingo-iOS-update.json")
        current = "v0.5.6-test-446c68ca-r45974974161"
        release = {"tag_name": current, "draft": False,
                   "assets": [{"name": n, "state": "uploaded", "size": 1} for n in names]}
        protected_draft = "v0.5.3-test-abcdef12-r123"
        older = ["v0.5.4-test-6ddf3f16-r45945942215", "v0.5.0-test-81905d9"]
        tags = [current, protected_draft, "v0.5.6-test-abcdef12-r5",
                "v0.5.7-test-abcdef12-r6", "v0.5.1", "custom-test", *older]
        self.assertEqual(plan([release, {"tag_name": protected_draft, "draft": True}], tags),
                         sorted(older))
        self.assertEqual(plan([release], [current]), [])
        for invalid in ([], [release, dict(release, tag_name=older[0])],
                        [dict(release, assets=[])], [dict(release, tag_name="v0.5.6")]):
            with self.assertRaises(ValueError):
                plan(invalid, tags)
        with self.assertRaises(ValueError):
            plan([release], older)
        workflow = code(ROOT/".github/workflows/publish-gamelingo-current.yml")
        self.assertIn("cleanup_only:", workflow)
        self.assertIn("!inputs.cleanup_only", workflow)
        self.assertIn("needs.release.result == 'success'", workflow)
        self.assertIn("inputs.cleanup_only && needs.release.result == 'skipped'", workflow)
        self.assertIn("gh api --paginate --slurp", workflow)
        self.assertIn('gh api --method DELETE "repos/$REPO/git/refs/tags/$old"', workflow)
        self.assertLess(workflow.index("python3 tools/cleanup_release_tags.py releases.json"),
                        workflow.index('gh api --method DELETE "repos/$REPO/git/refs/tags/$old"'))

    def test_build_and_publish_workflows_preserve_signing_and_validation(self):
        folder = ROOT/".github/workflows"
        workflow = code(folder/"audit-0.5-ci.yml")
        publisher = code(folder/"publish-gamelingo-current.yml")
        self.assertEqual({p.name for p in folder.glob("*.yml")},
                         {"audit-0.5-ci.yml", "publish-gamelingo-current.yml"})
        for platform in ("TV", "MOBILE"):
            for suffix in ("KEYSTORE_B64", "KEYSTORE_PASSWORD"):
                name = f"GAMELINGO_{platform}_{suffix}"
                self.assertEqual(workflow.count(name + ": ${{ secrets." + name + " }}"), 1)
        self.assertEqual(workflow.count("Persistent APK signing secrets missing on Master."), 2)
        self.assertNotIn("uses: ./.github/workflows/", workflow)
        self.assertIn("verify_tv_navigation.py upstream", workflow)
        self.assertIn("needs: [ios, android_mobile, google_tv]", workflow)
        self.assertEqual(workflow.count("needs: quick"), 3)
        self.assertGreaterEqual(workflow.count("github.event_name != 'pull_request'"), 5)
        self.assertIn("needs: [run_link, quick, package]", workflow)
        self.assertIn("actions/workflows/audit-0.5-ci.yml/runs", publisher)

    def test_mobile_and_ios_updates_share_release_contract(self):
        import runpy
        import tempfile
        import zipfile
        import plistlib
        android = code(TOOLS/"wire_mobile_update.py")
        self.assertIn("GameLingoTVUpdater.kt", android)
        self.assertIn("cz.caseycz.gamelingo.mobile", android)
        self.assertIn("GameLingo-Android-update.json", android)
        self.assertIn("patch_mobile_update(folder)", code(TOOLS/"wire_gamelingo_languages.py"))
        self.assertIn("GAMELINGO_MOBILE_KEYSTORE_B64", code(ROOT/".github/workflows/audit-0.5-ci.yml"))
        publish = code(ROOT/".github/workflows/publish-gamelingo-current.yml")
        self.assertIn("GameLingo-iOS-update.json", publish)
        self.assertIn("GameLingo-Android-update.json", publish)
        ios = code(IOS/"GameLingoUpdates.swift")
        self.assertIn("actual == update.metadata.sha256", ios)
        self.assertIn("manifest.versionCode > build", ios)
        self.assertIn("ShareLink(item: file)", ios)
        self.assertIn("updates.checkAtStartup()", code(IOS/"GameLingoHome.swift"))
        metadata = runpy.run_path(str(TOOLS/"create_ios_update_manifest.py"))["metadata"]
        with tempfile.TemporaryDirectory() as tmp:
            ipa = Path(tmp)/"GameLingo-iOS-v1.2.3-dev-unsigned.ipa"
            tag = "v1.2.3-test-abcdef12-r123"
            with zipfile.ZipFile(ipa, "w") as archive:
                archive.writestr("Payload/Test.app/Info.plist", plistlib.dumps({
                    "CFBundleIdentifier": "cz.caseycz.gamelingo.ios",
                    "CFBundleShortVersionString": "1.2.3", "CFBundleVersion": "7"}))
            manifest = metadata(ipa,tag,"1.2.3",7)
            self.assertEqual(manifest["versionCode"],7)
            self.assertEqual(manifest["size"],ipa.stat().st_size)
            with self.assertRaises(ValueError):
                metadata(ipa,tag,"1.2.3",8)
            with self.assertRaises(ValueError):
                metadata(ipa,tag,"1.2.4",7)
            apk = Path(tmp)/"GameLingo-Android-v1.2.3-dev-TEST.apk"
            apk.write_bytes(b"mobile fixture")
            apk_metadata = runpy.run_path(str(TOOLS/"create_tv_update_manifest.py"))["metadata"]
            badging = "package: name='cz.caseycz.gamelingo.mobile' versionCode='123' versionName='1.2.3-dev.123-abcdef12'"
            cert = "a"*64
            self.assertEqual(apk_metadata(apk,tag,"1.2.3",badging,cert,cert,"mobile")["package"],"cz.caseycz.gamelingo.mobile")
            with self.assertRaises(ValueError):
                apk_metadata(apk,tag,"1.2.3",badging,cert,"b"*64,"mobile")

    def test_tv_remote_control_and_receiver_modes(self):
        screen=code(TOOLS/"polish_all_screens.py")
        receiver=code(TOOLS/"redesign_gamelingo.py")
        tv=code(TOOLS/"GameLingoTVUi.kt")
        wiring=code(TOOLS/"wire_android_devices.py")
        for marker in ("nextFocusRightId", "nextFocusLeftId",
                       "Překlad z telefonu", "Přímo na TV",
                       "volby.post", "GameLingoTVUi.tile(",
                       "GameLingoTVUi.showSettings(this)"):
            self.assertIn(marker,screen)
        for marker in ("isFocusable = true", "setOnFocusChangeListener",
                       "showWelcome(", "Jazyk hry", "Jazyk překladu",
                       "showSettings(", "R.drawable.gamelingo_xbox",
                       "R.drawable.gamelingo_playstation"):
            self.assertIn(marker,tv)
        for marker in ("IPhoneTVPairing.startCode()", "PrijemService.statistikaPrijmu()",
                       "zpetNaVyber()", "GameLingoTVUi.action("):
            self.assertIn(marker,receiver)
        self.assertIn("GameLingoTVUi.showWelcome(this@MainActivity)",wiring)

    def test_tv_audit_followup_has_reachable_controls_and_binary_gate(self):
        receiver = code(TOOLS/"redesign_gamelingo.py")
        direct = code(TOOLS/"direct_tv_mode.py")
        settings = code(TOOLS/"GameLingoTVSettings.kt")
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        fit = code(TOOLS/"GameLingoTVFit.kt")
        for value in ("GameLingoTVFit(this, obsah)", "GameLingoQRDisplay.bitmap", "paticka.addView(prijemDiag", "versionName"):
            self.assertIn(value, receiver)
        self.assertIn("cards.chunked(3)", settings)
        self.assertIn("GameLingoTVCaptionStyle.show", settings)
        self.assertIn("GameLingoTVProfiles.show", settings)
        self.assertIn("GameLingoTVSettings.show(this,", direct)
        self.assertIn("GameLingoScreenTranslationService.open(this)", direct)
        self.assertIn("createScreenCaptureIntent", capture)
        self.assertIn("registerCallback", capture)
        self.assertNotIn("FLAG_SECURE", capture)
        self.assertIn("content.scaleX=factor", fit)
        self.assertIn("GITHUB_RUN_NUMBER", code(TOOLS/"brand_android.py"))
        self.assertIn("verify_tv_ui_apk.py", code(TOOLS.parent/".github/workflows/audit-0.5-ci.yml"))

    def test_tv_projection_waits_for_typed_foreground_and_success_before_background(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        activity = capture.split("class GameLingoScreenTranslationService", 1)[0]
        session = activity.split("private fun startSession", 1)[1]
        self.assertNotIn("OverlayService.spust", session)
        self.assertNotIn("moveTaskToBack", session)
        self.assertIn("GameLingoScreenTranslationService.running ->", activity)
        self.assertIn("GameLingoScreenTranslationService.startupFailed ->", activity)
        service = capture.split("class GameLingoScreenTranslationService", 1)[1]
        self.assertLess(service.index("notification()", service.index("starting = true")), service.index("ui.postDelayed(task, 500L)"))
        self.assertIn("foregroundServiceType", service)
        start = service.split("private fun beginCapture", 1)[1].split("private fun failStartup", 1)[0]
        self.assertLess(start.index("getMediaProjection"), start.index("createVirtualDisplay"))
        self.assertLess(start.index("createVirtualDisplay"), start.index("OverlayService.spust"))
        self.assertIn("pendingStart?.let", service)
        self.assertIn("FOREGROUND_SERVICE_MEDIA_PROJECTION", code(TOOLS/"direct_tv_mode.py"))

    def test_standalone_tv_ocr_and_translation_are_independent_and_menu_filtered(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        wiring = code(TOOLS/"direct_tv_mode.py")
        self.assertIn("prectiPosledniTitulek(cropped, hotovo", capture)
        self.assertIn("standaloneScreen = true", capture)
        self.assertIn("if (!paused && !ocrBusy && !capturePending && wantFrame.get() == 0)", capture)
        self.assertIn("translationBusy", capture)
        self.assertIn("value == candidate", capture)
        self.assertIn("Starý výsledek zahozen", capture)
        self.assertIn("coverOriginal = settings.prekryvat", capture)
        self.assertIn("getRealMetrics(metrics)", capture)
        self.assertIn("kusy.size > 8", wiring)
        self.assertIn("kod == sourceLanguage", wiring)
        self.assertNotIn("fun prectiSamostatnyTitulek", wiring)
        self.assertIn("lastOcrMs", capture)
        self.assertIn("lastTranslationMs", capture)

    def test_standalone_tv_reuses_all_four_profile_regions(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        layout = code(TOOLS/"GameLingoDirectTVLayout.kt")
        profiles = code(TOOLS/"GameLingoTVProfiles.kt")
        for marker in ("GameLingoDirectTVLayout.blue(this)", "GameLingoDirectTVLayout.green(this)",
                       "GameLingoDirectTVLayout.yellow(this)", "GameLingoDirectTVLayout.orange(this)",
                       "Volby.zapnuto", "Volby.rezimText", "prectiVolby(cropped", "ACTION_SET_VOLBY",
                       "GameLingoTVProfiles.chooseForStart(a)", "if (yellow) yellowRoi else roi"):
            self.assertIn(marker, capture)
        chooser = profiles.split("fun chooseForStart(", 1)[1].split("fun saveCurrent(", 1)[0]
        self.assertIn("names.getOrNull(index - 1)", chooser)
        self.assertIn("all(a).optJSONObject(it)", chooser)
        self.assertIn("restore(a, profile)", chooser)
        self.assertNotIn("getJSONObject(", chooser)
        self.assertIn("if (busy()) return@select", chooser)
        self.assertIn("fun greenBounds", layout)
        self.assertIn("wantFrame.getAndSet(0)", capture)
        self.assertIn("yellowEpoch", capture)
        self.assertIn("GameLingoTVProfiles.chooseForStart(this)", code(TOOLS/"direct_tv_mode.py"))
        self.assertIn("suppressStandaloneCapture(true)", capture)
        self.assertIn("settings.casPribehuProcenta", capture)
        self.assertIn("PribehovaRelace(", capture)
        self.assertIn("shownUntil", capture)

    def test_tv_uses_shared_mobile_settings_and_persists_layout_locks(self):
        tv = code(TOOLS/"GameLingoTVSettings.kt")
        settings = code(TOOLS/"GameLingoSettings.kt")
        profiles = code(TOOLS/"GameLingoTVProfiles.kt")
        layout = code(TOOLS/"GameLingoDirectTVLayout.kt")
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        self.assertIn("GameLingoOCRSettings.show(a)", tv)
        self.assertIn("GameLingoSettings.showTranslation(a", tv)
        for marker in ("settings.napovedy", "settings.prekryvat", "settings.casPribehuProcenta",
                       "Volby.rezimText", "OblastTitulku.rucne"):
            self.assertIn(marker, settings)
        for marker in ("blueLocked", "yellowLocked", "greenLocked", "orangeLocked"):
            self.assertIn(marker, profiles)
        self.assertIn("Obnovit oblast", layout)
        self.assertIn("GameLingoTVProfiles.saveCurrent(a)",layout)
        self.assertIn("fun saveCurrent",profiles)
        for marker in ("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_CENTER", "TYPE_APPLICATION_OVERLAY", "FLAG_NOT_TOUCHABLE", "Uložit do profilu", "removePreview()", "30000L"):
            self.assertIn(marker, layout)
        self.assertIn("GameLingoOCRConfig.expandSearch", capture)
        self.assertIn("blockedHome", capture)
        self.assertIn("clearHint()", capture)

    def test_tv_help_has_single_focus_style_and_fullscreen_destination(self):
        help_ui=code(TOOLS/"GameLingoHelp.kt")
        self.assertIn("fun layout(activity: Activity",help_ui)
        self.assertIn("GameLingoTVUi.panel(activity,focused)",help_ui)
        self.assertIn("setOnFocusChangeListener",help_ui)
        self.assertIn('GameLingoTVUi.page(activity,GameLingoLanguages.ui(activity, "Nápověda") + " · " + title',help_ui)
        self.assertIn('GameLingoTVUi.header(a, heading, back, help)',code(TOOLS/"GameLingoTVSettings.kt"))
        self.assertIn('helpTopic = "ocr"',code(TOOLS/"GameLingoOCRSettings.kt"))
        self.assertIn("GameLingoTVUi.header(a, GameLingoTVUi.title",code(TOOLS/"GameLingoDirectTVLayout.kt"))
        self.assertIn("Independent help glyph",code(TOOLS/"audit_tv_ui.py"))

    def test_tv_receiver_and_editor_fixed_header_and_separate_stop(self):
        receiver = code(TOOLS/"redesign_gamelingo.py")
        nav = code(TOOLS/"tv_navigation.py")
        editor = code(TOOLS/"GameLingoDirectTVLayout.kt")
        self.assertIn('GameLingoTVUi.header(this, logo, zastav, GameLingoHelp.button(this, "tv"))',receiver)
        self.assertNotIn("paticka.addView(zastav)",receiver)
        self.assertIn("paticka.addView(zastavPrijem)",nav)
        self.assertIn('val cancel = button("Zrušit"){dialog.dismiss()}',editor)
        self.assertIn("GameLingoTVUi.header(activity, heading, back",code(TOOLS/"GameLingoFirstRun.kt"))

    def test_tv_profile_cancel_keeps_parent_and_editor_returns_to_list(self):
        source = code(TOOLS/"GameLingoTVProfiles.kt")
        lane = source.split("fun show(a: Activity)",1)[1]
        self.assertNotIn("page?.dismiss()\n                    when(action)",lane)
        self.assertIn("all(a).optJSONObject(name)",lane)
        self.assertIn('onClose = { if (!a.isFinishing && !a.isDestroyed) show(a) }',source)
        self.assertIn("GameLingoGlossaryUi.edit(a,name){page?.dismiss();show(a)}",lane)

    def test_tv_fullscreen_pages_and_global_dialog_theme(self):
        ui=code(TOOLS/"GameLingoTVUi.kt")
        wiring=code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("fun page(a: Activity",ui)
        for module in ("GameLingoTVCaptionStyle.kt","GameLingoHistoryUi.kt","GameLingoTVSettings.kt","GameLingoTranslationCache.kt"):
            self.assertIn("GameLingoTVUi.page(a",code(TOOLS/module))
        for marker in ("install_tv_theme(root)","GameLingoTVTheme","GameLingoTVAlert","state_focused","gamelingo_tv_focus"):
            self.assertIn(marker,wiring)
        self.assertIn('GameLingoTVUi.mountPage(activity, dialog, tr("Překlad"), content, helpTopic = "translation")',code(TOOLS/"GameLingoSettings.kt"))

    def test_tv_diagnostics_distinguishes_stopped_resources_from_last_session_counters(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        service = capture.split("class GameLingoScreenTranslationService",1)[1]
        cleanup = service.split("override fun onDestroy()",1)[1].split("companion object",1)[0]
        for marker in ("releaseDisplay.isSuccess", "releaseProjection.isSuccess",
                       "stopForeground(STOP_FOREGROUND_REMOVE)", "stopForegroundResult.isSuccess"):
            self.assertIn(marker, cleanup)
        self.assertIn("reportView.text = report(a)", service)
        self.assertIn("reportView.removeCallbacks(reportRefresh)", service)

    def test_tv_diagnostic_display_localizes_without_rewriting_capture_state(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        settings = code(TOOLS/"GameLingoTVSettings.kt")
        languages = code(TOOLS/"GameLingoLanguages.kt")
        self.assertIn('localizedStatus(a, GameLingoScreenTranslationService.status)', settings)
        self.assertIn('reportView.text = report(a)', capture)
        self.assertIn('status.startsWith("Systém ukončil")', capture)
        self.assertIn('foregroundState.startsWith("Aktivní")', capture)
        self.assertIn('if (lastRaw.isNotBlank()) "\\nOCR: $lastRaw"', capture)
        for key in ("Samostatný překlad TV", "Diagnostika TV", "Čekám na první snímek", "Přijaté snímky:"):
            self.assertIn(key, json.loads(code(ROOT/"shared/locales/cs.json")))

    def test_tv_standalone_requires_consent_and_uses_only_ocr(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        wiring = code(TOOLS/"direct_tv_mode.py")
        self.assertFalse((TOOLS/"GameLingoSubtitleAccessibility.kt").exists())
        for forbidden in ("takeScreenshot(", "dispatchGesture(", "performAction(", "AudioRecord", "RECORD_AUDIO",
                          "GameLingoSubtitleAccessibility", "Pouze text titulků"):
            self.assertNotIn(forbidden, capture)
        for marker in ("resultCode == RESULT_OK", "HandlerThread", "setOnImageAvailableListener",
                       "acquireLatestImage", "finally { image.close() }", "plane.rowStride",
                       "received.incrementAndGet()", "decoded.incrementAndGet()", "ticket == epoch",
                       "Spustit překlad TV", "Diagnostika samostatného OCR", "Test překryvu"):
            self.assertIn(marker, capture)
        for marker in ("ACTION_TEST_OVERLAY", "ACTION_STOP_TEST_OVERLAY", 'showText("Testovací titulek", 0L)',
                       "TYPE_APPLICATION_OVERLAY", "FLAG_NOT_TOUCHABLE", "recordOverlayTest"):
            self.assertIn(marker, wiring)
        self.assertNotIn("android.permission.BIND_ACCESSIBILITY_SERVICE", wiring)
        self.assertIn('android:foregroundServiceType="mediaProjection"', wiring)
        self.assertIn("GameLingoScreenTranslationService.running ||", wiring)
        self.assertIn("Obsolete accessibility implementation included", code(TOOLS/"verify_tv_ui_apk.py"))

    def test_tv_home_only_shows_real_saved_consoles_and_does_not_redirect(self):
        home=code(TOOLS/"polish_all_screens.py")
        wiring=code(TOOLS/"direct_tv_mode.py")
        for marker in ("GameLingoPlayStationConsoles.list(this)", "consoleCards.isNotEmpty()",
                       "Zatím nemáš přidanou žádnou konzoli.", "it.first !in hiddenXboxIds",
                       "else if (PrijemService.bezi) ukazPrijem() else hratSTelefonem()"):
            self.assertIn(marker,home)
        self.assertIn("Active phone reception stays in the background",wiring)
        self.assertNotIn("Přidat PlayStation a spárovat osmimístným PINem",home)

    def test_mobile_onboarding_resume_and_real_source_selection(self):
        first = code(TOOLS/"GameLingoFirstRun.kt")
        wiring = code(TOOLS/"wire_android_devices.py")
        home = code(TOOLS/"redesign_gamelingo.py")
        center = code(TOOLS/"GameLingoDeviceCenter.kt")
        self.assertIn('savedChoices.getInt("step", 0)', first)
        self.assertIn("activeDialog?.isShowing", first)
        self.assertIn("resume incomplete onboarding on dashboard", wiring)
        self.assertIn("store.zapamatujVyber(candidate.id)", first)
        self.assertIn("RemotePlayService.preferXbox(this)", home)
        self.assertIn('RemotePlayService.prepni(this, primaryFamily == "Xbox")', home)
        self.assertIn("RemotePlayService.prepni(c, false)", center)
        self.assertIn("RemotePlayService.prepni(c, true)", center)
        self.assertIn("translationLanguageEditable", code(TOOLS/"GameLingoLanguages.kt"))

    def test_mobile_tv_manual_pairing_and_forget_without_delete(self):
        center = code(TOOLS/"GameLingoDeviceCenter.kt")
        self.assertIn('TvProfil("manual:" + ip', center)
        self.assertIn('android.util.Patterns.IP_ADDRESS.matcher(ip).matches()', center)
        unpair = center.split("pinSupported && option == 4", 1)[1].split("} else {", 1)[0]
        self.assertIn("GameLingoTVPairing.forget(c, tv.ip)", unpair)
        self.assertNotIn("tvStore.odeber", unpair)
        preview = center.split("private fun showTestCaption", 1)[1].split("/** Shared content", 1)[0]
        self.assertIn("GameLingoTVPairing.verify(c, tv)", preview)
        self.assertIn("Propojeni.Vysilac.ukazka(sample)", preview)
        self.assertIn("RemotePlayService.aktivni", preview)

    def test_mobile_and_tv_device_rename_share_actions(self):
        center = code(TOOLS/"GameLingoDeviceCenter.kt")
        self.assertNotIn('if(tenFoot) arrayOf(GameLingoLanguages.ui(c, "Použít")', center)
        self.assertIn('rename(c,"ps",ps.ip,ps.name)', center)
        self.assertIn('rename(c,"xbox",xbox.id,xbox.jmeno)', center)
        wiring = code(TOOLS/"wire_android_devices.py")
        self.assertIn("bytes.size != 8", wiring)
        self.assertIn("android.util.Base64.encodeToString(bytes", wiring)

    def test_android_playstation_registration_and_login_pin(self):
        wiring = code(TOOLS/"wire_android_devices.py")
        prompt = code(TOOLS/"GameLingoPSLoginPIN.kt")
        for marker in ("psHost", "psType", "psName", "consoleName, ip",
                       "requesting.setLoginPin(pin)", "session === requesting",
                       "GameLingoPSLoginPIN.resume(this)", "GameLingoPSLoginPIN.pause(this)",
                       "GameLingoPSLoginPIN.clear(this)"):
            self.assertIn(marker, wiring)
        self.assertIn("InputFilter.LengthFilter(4)", prompt)
        self.assertIn("isSaveEnabled = false", prompt)
        self.assertIn("pending !== request", prompt)
        self.assertIn("field?.text?.clear()", prompt)

    def test_mobile_console_uses_shared_timing_and_ocr_presets(self):
        settings = code(TOOLS/"GameLingoSettings.kt")
        ocr = code(TOOLS/"GameLingoOCRSettings.kt")
        profile = code(TOOLS/"GameLingoProfiles.kt")
        direct = code(TOOLS/"direct_tv_mode.py")
        self.assertNotIn('if(television) {\n            add(row(activity,"Pevná doba titulku"', settings)
        self.assertNotIn('if(GameLingoTVUi.isTV(activity)) {\n            action("Výchozí oblast"', ocr)
        for key in ("autoRegion", "fixedTiming", "seconds", "skipOld", "maxAge"):
            self.assertIn('put("' + key + '"', profile)
            self.assertIn('data.opt', profile)
        self.assertIn('GameLingoOCRConfig.saveTiming(c,', profile)
        self.assertIn('} else GameLingoOCRConfig.preset(this)', direct)
        self.assertIn('ukaz(cesky, GameLingoOCRConfig.displayMs(this,4000L)', direct)
        self.assertIn('if (GameLingoOCRConfig.expired(this,', direct)
        self.assertNotIn('if (!telefon && GameLingoOCRConfig.expired', direct)
        self.assertNotIn('if (telefon) 4000L else GameLingoOCRConfig.displayMs', direct)

    def test_tv_ios_parity_settings_are_persisted_and_used(self):
        config=code(TOOLS/"GameLingoOCRConfig.kt")
        capture=code(TOOLS/"GameLingoScreenTranslationService.kt")
        profile=code(TOOLS/"GameLingoTVProfiles.kt")
        for key in ("autoRegion","fixedTiming","seconds","skipOld","maxAge"):
            self.assertIn(key,profile)
        self.assertIn("GameLingoOCRConfig.preset(this)",capture)
        self.assertIn("GameLingoOCRConfig.displayMs",capture)
        self.assertIn("GameLingoOCRConfig.expired",capture)
        self.assertIn("GameLingoGlossaryStore.apply",capture)
        self.assertIn("activateGlossary",profile)
        self.assertIn("GameLingoOCRConfig.displayMs",code(TOOLS/"direct_tv_mode.py"))
        self.assertIn("Náhled na TV",code(TOOLS/"GameLingoTVCaptionStyle.kt"))
        self.assertIn("Přejmenovat konzoli",code(TOOLS/"GameLingoDeviceCenter.kt"))
        self.assertIn("GameLingoDeviceCenter.consoleName",code(TOOLS/"polish_all_screens.py"))

    def test_tv_review_fixes_focus_opacity_and_profile_editing(self):
        style=code(TOOLS/"GameLingoTVCaptionStyle.kt")
        self.assertIn("opacityBar.progress=percent",style)
        self.assertIn('label.text = "${tr("Neprůhlednost pozadí")}: $percent %"',style)
        self.assertIn('(p.getInt("opacity",230).coerceIn(0,255)*100+127)/255',style)
        self.assertIn("ViewGroup.FOCUS_BLOCK_DESCENDANTS",code(TOOLS/"GameLingoOCRSettings.kt"))
        self.assertIn("ViewGroup.FOCUS_BLOCK_DESCENDANTS",code(TOOLS/"GameLingoSettings.kt"))
        self.assertIn("Upravit další nastavení",code(TOOLS/"GameLingoTVProfiles.kt"))
        self.assertIn("GameLingoTVUi.panel(a,focused)",code(TOOLS/"GameLingoDirectTVLayout.kt"))

    def test_tv_device_manager_shows_phones_instead_of_other_tvs(self):
        manager=code(TOOLS/"GameLingoDeviceCenter.kt")
        tv=code(TOOLS/"direct_tv_mode.py")
        self.assertIn('arrayOf("Xbox", "PlayStation 5 / 4", GameLingoLanguages.ui(c, "Telefon · QR / PIN"))',manager)
        self.assertIn('label(c, GameLingoLanguages.ui(c, "Telefony"), 16f)',manager)
        self.assertIn("TV_RECEIVER_PHONE_ROWS",manager)
        self.assertIn("IPhoneTVPairing.phones(c)",tv)
        self.assertIn("IPhoneTVPairing.unpair(c, phone.id)",tv)
        self.assertIn("return root",manager.split("TV_RECEIVER_PHONE_ROWS",1)[1].split('label(c, GameLingoLanguages.ui(c, "Televize")',1)[0])

    def test_tv_mobile_downloads_use_public_prerelease_assets_and_two_home_actions(self):
        qr=code(TOOLS/"GameLingoQRDisplay.kt")
        wiring=code(TOOLS/"wire_android_devices.py")
        for marker in ("GameLingoMobileDownloads", "/releases?per_page=20",
                       "GameLingo-iOS-", "GameLingo-Android-", "published_at",
                       'release.optBoolean("draft")', "QRCodeWriter().encode(target",
                       "Znovu ověřit vydání"):
            self.assertIn(marker,qr)
        self.assertNotIn('"/releases/latest"',qr)
        for marker in ("GameLingoMobileDownloads.show(this)", "Stáhnout aplikaci",
                       'GameLingoTVUi.action(this, GameLingoLanguages.ui(this, "Zařízení"))', "dlazdiceTel.nextFocusDownId=stahnoutMobil.id",
                       "dlazdiceTv.nextFocusDownId=spravaZarizeni.id"):
            self.assertIn(marker,wiring)

    def test_tv_device_management_uses_branded_picker_for_all_device_lists(self):
        manager=code(TOOLS/"GameLingoDeviceCenter.kt")
        self.assertIn("GameLingoTVUi.select(c, heading, options, onSelect = onSelect)",manager)
        for marker in ('chooseDevice(c, GameLingoLanguages.ui(c, "Přidat zařízení"), options)', 'chooseDevice(c, consoleName(c,"ps",ps.ip,ps.name)',
                       'chooseDevice(c, consoleName(c,"xbox",xbox.id,xbox.jmeno)', "chooseDevice(c, tv.jmeno"):
            self.assertIn(marker,manager)

    def test_android_can_remove_and_restore_xbox_without_signing_out(self):
        manager=code(TOOLS/"GameLingoDeviceCenter.kt")
        for marker in (
            'arrayOf(GameLingoLanguages.ui(c, "Použít"), GameLingoLanguages.ui(c, "Připojit"), GameLingoLanguages.ui(c, "Odebrat"), GameLingoLanguages.ui(c, "Přejmenovat"))',
            '"hiddenXboxIds"',
            'filterNot { it.id in hiddenXboxIds }',
            '"Vrátit odebrané Xboxy"',
            'update.remove("selectedXboxId")',
            'RemotePlayService.aktivni',
            'getStringSet("hiddenXboxIds"',
        ):
            self.assertIn(marker,manager)
        self.assertNotIn("XboxUcet.odhlas",manager)

    def test_single_native_playstation_login_pin_prompt(self):
        home=code(IOS/"GameLingoHome.swift")
        self.assertIn('.alert("Přihlašovací PIN PlayStation"',home)
        self.assertIn('model.playStation.submitLoginPIN(psLoginPIN)',home)
        self.assertNotIn('playStationLoginPIN',home)
        self.assertNotIn('GLHeader(title: "PIN PlayStation"',home)

    def test_original_neon_screens_are_native_swiftui(self):
        theme=code(IOS/"GameLingoVisualSystem.swift")
        home=code(IOS/"GameLingoHome.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        regions=code(IOS/"SubtitleSettingsView.swift")
        devices=code(IOS/"GameLingoDevicesPanel.swift")
        settings=code(IOS/"SettingsHomeView.swift")
        for component in ("GLNeonBackground", "GLNeonPanel", "GLBrandWordmark",
                          "GLPlatformBadge", "GLDetailRow"):
            self.assertIn("struct "+component,theme)
        for marker in ("heroBanner", "GLBrandWordmark(compact: true)",
                       "GLPlatformBadge(family: .xbox",
                       "GLPlatformBadge(family: .playStation",
                       "GLNeonPanel(emphasized: connected)", "GLNeonBackground()"):
            self.assertIn(marker,home)
        for marker in ("GLBrandWordmark()", "GLPlatformBadge(family: family",
                       "case 1: languageStep", "consoleStep", "outputStep",
                       "ForEach(GameLingoUIContract.setupSteps.indices", "GLNeonBackground()"):
            self.assertIn(marker,setup)
        for marker in ("GLNeonTitle(", "GLNeonPanel(emphasized: true)",
                       "ForEach(Area.allCases", "model.choicesOutputArea"):
            self.assertIn(marker,regions)
        self.assertIn("GLPlatformBadge(family: console.family",devices)
        for marker in ("GLDetailRow(icon:", "GameLingoLanguageSettingsView",
                       "GameLingoLicenseView", "GLNeonBackground()"):
            self.assertIn(marker,settings)

    def test_configured_languages_change_real_ocr_and_translation(self):
        language=code(IOS/"GameLingoLanguages.swift")
        model=code(IOS/"PlayCaptionIOSApp.swift")
        pipeline=code(IOS/"CaptionPipeline.swift")
        settings=code(IOS/"SettingsHomeView.swift")
        setup=code(IOS/"GameLingoSetupView.swift")
        self.assertIn("enum GLGameLanguage",language)
        self.assertIn("enum GLTargetLanguage",language)
        for needle in ("@Published var gameLanguage", "@Published var targetLanguage",
                       "gameLanguage: activeGameLanguage", "to: activeTargetLanguage"):
            self.assertIn(needle,model)
        for needle in ("gameLanguage.visionIdentifier", "source.rawValue",
                       "target.rawValue", 'URLQueryItem(name: "sl"',
                       'URLQueryItem(name: "tl"'):
            self.assertIn(needle,pipeline)
        for view in (settings,setup):
            self.assertIn("$model.gameLanguage",view)
            self.assertIn("$model.targetLanguage",view)
        self.assertIn('selection: $model.appLanguage',setup)
        self.assertIn('selection: $model.appLanguage',settings)
        self.assertIn('Vítej v GameLingo',setup)
        self.assertIn('ForEach(GameLingoUIContract.setupSteps.indices',setup)

    def test_pinned_native_source_and_build(self):
        workflow=code(ROOT/".github/workflows/audit-0.5-ci.yml")
        project=code(ROOT/"ios/GameLingoIOS/project.yml")
        self.assertIn('482611539977496cad047560c9bc1ebc5809a566',workflow)
        self.assertIn('Build native PlayStation',workflow)
        self.assertIn('GameLingoPSBridge.h',project)
        self.assertIn('CFBundleShortVersionString: "$(MARKETING_VERSION)"', project)

    def test_caption_retransmission_preserves_original_expiry(self):
        ios = code(IOS/"PlayCaptionTV.swift")
        android = code(TOOLS/"wire_android_devices.py")
        self.assertIn("let expiry = ProcessInfo.processInfo.systemUptime", ios)
        self.assertIn("repeatFields[2] = String(remaining)", ios)
        self.assertIn("guard remaining > 0 else { break }", ios)
        self.assertIn("captionDeadline - android.os.SystemClock.elapsedRealtime()", android)
        self.assertIn("text.isNotEmpty() && remaining == 0L", android)
        self.assertIn('text = ""; doba = 0L; barvy = null; captionDeadline = 0L', android)
        self.assertIn("checkedPortAt == 0L", code(TOOLS/"GameLingoTVPairing.kt"))

    def test_point17_blue_menu_switch_controls_android_column_rejection(self):
        import runpy
        patch = runpy.run_path(str(TOOLS/"redesign_gamelingo.py"))["wire_dialogue_menu_filter"]
        condition = "if (vybrane.size >= 3 && vybrane.count { VyberTitulku.pocetSlov(it.text) <= 2 } >= vybrane.size - 1) {"
        yellow = "fun prectiVolby(obraz: Bitmap, hotovo: (List<Pair<String, Rect>>) -> Unit, chyba: (String) -> Unit) {}"
        fixture = yellow + "\nfun prectiPosledniTitulek() {\n    " + condition + "\n    }\n}"
        patched = patch(fixture)
        self.assertEqual(patched, fixture.replace("if (vybrane.size", "if (GameLingoOCRConfig.filterMenuText && vybrane.size"))
        self.assertIn(yellow, patched)
        with self.assertRaises(RuntimeError):
            patch(yellow)
        with self.assertRaises(RuntimeError):
            patch(fixture + fixture)
        self.assertIn("wire_dialogue_menu_filter(reader.read_text", code(TOOLS/"redesign_gamelingo.py"))
        ios = code(IOS/"CaptionPipeline.swift")
        self.assertIn("if filterMenus && looksLikeMenu", ios)
        self.assertNotIn("filterMenus", ios.split("func recognizeOptions(", 1)[1].split("private func looksLikeMenu", 1)[0])

    def test_point18_yellow_filter_is_opt_in_and_independent(self):
        import runpy
        patch = runpy.run_path(str(TOOLS/"redesign_gamelingo.py"))["wire_yellow_control_filter"]
        yellow = "    fun prectiVolby() {\n                })\n            }\n            .addOnFailureListener"
        dialogue = "    class Titulek(\n                })\n            }\n            .addOnFailureListener"
        result = patch(yellow + "\n" + dialogue)
        self.assertTrue(result.endswith(dialogue))
        self.assertIn("GameLingoYellowOCRFilter.keeps(it.first, GameLingoOCRConfig.filterChoiceControls)", result)
        self.assertIn('store.getBoolean("filterChoiceControls", false)', code(TOOLS/"GameLingoOCRConfig.kt"))
        self.assertIn("YellowOCRFilter.keeps($0, enabled: filterControls)", code(IOS/"CaptionPipeline.swift"))
        self.assertIn("if oldValue != filterChoiceControls { invalidateOptions() }", code(IOS/"PlayCaptionIOSApp.swift"))
        for profile in ("GameLingoTVProfiles.kt", "GameLingoProfiles.kt"):
            self.assertIn('optBoolean("filterChoiceControls", false)', code(TOOLS/profile))

    def test_point20_blue_minimum_height_is_persistent_and_opt_in(self):
        config = code(TOOLS/"GameLingoOCRConfig.kt")
        self.assertIn('store.getInt("minBlueHeight", 0).coerceIn(0, 5)', config)
        self.assertIn("GameLingoOCRConfig.minBlueHeight", code(TOOLS/"redesign_gamelingo.py"))
        pipeline = code(IOS/"CaptionPipeline.swift")
        lane = pipeline.split("func recognize(", 1)[1].split("func recognizeOptions(", 1)[0]
        self.assertIn("if minimumHeight > 0 { request.minimumTextHeight", lane)
        self.assertIn("minimumHeight: Int = 0", lane)
        for profile in ("GameLingoProfiles.kt", "GameLingoTVProfiles.kt"):
            self.assertIn('optInt("minBlueHeight", 0)', code(TOOLS/profile))

    def test_point21_yellow_minimum_height_is_persistent_and_opt_in(self):
        config = code(TOOLS/"GameLingoOCRConfig.kt")
        self.assertIn('store.getInt("minYellowHeight", 0).coerceIn(0, 5)', config)
        self.assertIn("GameLingoOCRConfig.minYellowHeight", code(TOOLS/"redesign_gamelingo.py"))
        pipeline = code(IOS/"CaptionPipeline.swift")
        lane = pipeline.split("func recognizeOptions(", 1)[1]
        self.assertIn("if minimumHeight > 0 { request.minimumTextHeight", lane)
        self.assertIn("minimumHeight: Int = 0", lane)
        for profile in ("GameLingoProfiles.kt", "GameLingoTVProfiles.kt"):
            self.assertIn('optInt("minYellowHeight", 0)', code(TOOLS/profile))

    def test_point23_filter_resets_preserve_other_area_and_geometry(self):
        android = code(TOOLS/"GameLingoOCRSettings.kt")
        blue = android.split('action("Obnovit filtry modré oblasti"', 1)[1].split('action("Obnovit filtry žluté oblasti"', 1)[0]
        yellow = android.split('action("Obnovit filtry žluté oblasti"', 1)[1].split('toggle("Rozšířit hledání"', 1)[0]
        self.assertIn("setIgnoredLines(activity, false", blue)
        self.assertIn("setIgnoredLines(activity, true", yellow)
        self.assertNotIn("setMinYellowHeight", blue)
        self.assertNotIn("setMinBlueHeight", yellow)
        self.assertNotIn("OblastTitulku", blue + yellow)
        self.assertNotIn("Volby.oblast", blue + yellow)
        ios = code(IOS/"SettingsHomeView.swift")
        self.assertIn('Button("Obnovit filtry modré oblasti")', ios)
        self.assertIn('Button("Obnovit filtry žluté oblasti")', ios)

    def test_point24_previous_dialogue_only_changes_display(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        self.assertIn("self.dialogueWindow.render(source: reading.english, translation: czech", model)
        self.assertIn("original: reading.english, translated: czech", model)
        self.assertIn("self.tv.sendSubtitle(displayText", model)
        self.assertIn("dialogueWindow.reset()", model)
        wiring = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("if (dobry) dialogueWindow.render(original, corrected", wiring)
        self.assertIn("!nast.prekryvat", wiring)
        self.assertIn("translationContext.toString()", wiring)
        self.assertIn("!coverOriginal && !useAutomaticOutput", code(TOOLS/"GameLingoScreenTranslationService.kt"))

    def test_point26_source_disappearance_is_opt_in(self):
        config = code(TOOLS/"GameLingoOCRConfig.kt")
        self.assertIn('getBoolean("hideOnSourceGone", false)', config)
        model = code(IOS/"PlayCaptionIOSApp.swift")
        self.assertIn("guard hideOnSourceGone, let deadline = dialogueDisplayDeadline", model)
        self.assertIn("if (GameLingoOCRConfig.hideOnSourceGone(this))", code(TOOLS/"wire_gamelingo_languages.py"))
        standalone = code(TOOLS/"GameLingoScreenTranslationService.kt")
        self.assertIn("!storyMode && GameLingoOCRConfig.hideOnSourceGone(this)", standalone)

    def test_point27_source_gone_delay_never_extends_display_deadline(self):
        model = code(IOS/"PlayCaptionIOSApp.swift")
        self.assertIn("let target = min(deadline, now + Double", model)
        self.assertIn("latestDeadline: target", model)
        self.assertIn("let deadline = min(now + seconds, latestDeadline ?? .infinity)", model)
        android = code(TOOLS/"wire_gamelingo_languages.py")
        self.assertIn("sourceGoneDeadline == 0L", android)
        self.assertIn("minOf(zbyva, (sourceGoneDeadline - now)", android)
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        self.assertIn("shownUntil = minOf(shownUntil, now + GameLingoOCRConfig.sourceGoneDelay(this)", capture)

    def test_review_confirmed_absence_rearms_same_dialogue_and_short_yellow_choices(self):
        capture = code(TOOLS/"GameLingoScreenTranslationService.kt")
        blank = capture.split("if (value.isBlank()) {", 1)[1].split("blankFrames = 0;", 1)[0]
        self.assertIn('epoch++; candidate = ""; shown = ""', blank)
        self.assertNotIn("shownUntil =", blank)
        self.assertNotIn('render("")', blank)
        pipeline = code(IOS/"CaptionPipeline.swift")
        yellow = pipeline.split("func recognizeOptions(", 1)[1]
        self.assertIn("YellowOCRFilter.hasReadableText($0)", yellow)
        self.assertIn(">= 2", code(IOS/"YellowOCRFilter.swift"))

    def test_051_bounded_discovery_diagnostics_and_frame_latency(self):
        pairing = code(TOOLS/"GameLingoTVPairing.kt")
        self.assertIn("val remaining = deadline - SystemClock.elapsedRealtime()", pairing)
        self.assertIn("if (remaining <= 0L) return@use cached", pairing)
        self.assertIn("packet.address == address && packet.port == port", pairing)
        diagnostics = code(TOOLS/"GameLingoDiagnostics.kt")
        self.assertIn("GameLingoTVPairing.connected(savedTv.ip)", diagnostics)
        self.assertIn("GameLingoTVPairing.verify(activity, selected)", diagnostics)
        model = code(IOS/"PlayCaptionIOSApp.swift")
        self.assertEqual(model.count("frameReceivedAt: frame.receivedAt"), 2)
        self.assertEqual(model.count("systemUptime - frameReceivedAt"), 2)
        self.assertIn("Dialog od snímku k předání", model)
        self.assertIn("Herní text od snímku k předání", model)

    def test_release_version_consistency(self):
        subprocess.run([sys.executable, str(TOOLS/"sync_version.py"), "--check"], check=True, cwd=ROOT)
        sys.path.insert(0, str(TOOLS))
        try:
            from sync_version import bumped, ios_project
            self.assertEqual(bumped({"version": "0.5.9", "build": 9}, "patch"), {"version": "0.5.10", "build": 10})
            self.assertEqual(bumped({"version": "0.5.9", "build": 9}, "minor"), {"version": "0.6.0", "build": 10})
            self.assertEqual(bumped({"version": "0.5.9", "build": 9}, "major"), {"version": "1.0.0", "build": 10})
            prepared = ios_project(code(ROOT/"ios/GameLingoIOS/project.yml"), {"version": "9.8.7", "build": 42})
            self.assertIn('CFBundleShortVersionString: "9.8.7"', prepared)
            self.assertIn('CFBundleVersion: "42"', prepared)
            with self.assertRaises(ValueError):
                bumped({"version": "0.5.9", "build": 2000}, "patch")
        finally:
            sys.path.pop(0)

    def test_no_fake_ps5_readiness(self):
        all_ios="\n".join(code(path) for path in IOS.rglob("*.swift"))
        self.assertNotIn('PS5 – coming soon',all_ios)
        self.assertNotIn('GameLingoPlayStationStatus.isReady',all_ios)

if __name__=="__main__":
    unittest.main()
