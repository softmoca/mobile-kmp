import ComposeApp
import CoreLocation
import ImageIO
import MapKit
import PDFKit
import PhotosUI
import Security
import UIKit
import UniformTypeIdentifiers

final class IOSServices: NSObject, IOSPlatformBridge, PHPickerViewControllerDelegate, CLLocationManagerDelegate, UIDocumentPickerDelegate {
    private var photoResult: ((PickedPhoto?, String?) -> Void)?
    private var locationResult: ((Coordinates?, String?) -> Void)?
    private var saveResult: ((String?) -> Void)?
    private let locationManager = CLLocationManager()
    private var locationTimeout: DispatchWorkItem?
    private var tokenQuery: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "\(Bundle.main.bundleIdentifier ?? "kr.jachwisunbae.app").session",
         kSecAttrAccount as String: "access-token"]
    }

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func readToken() -> String? {
        var query = tokenQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func writeToken(token: String) -> String? {
        let attributes: [String: Any] = [
            kSecValueData as String: Data(token.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        var status = SecItemUpdate(tokenQuery as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(tokenQuery.merging(attributes) { _, value in value } as CFDictionary, nil)
        }
        return status == errSecSuccess ? nil : "로그인 정보를 안전하게 저장하지 못했습니다. (\(status))"
    }

    func clearToken() -> String? {
        let status = SecItemDelete(tokenQuery as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound ? nil : "로그인 정보를 삭제하지 못했습니다. (\(status))"
    }

    // ImageIO decodes a bounded thumbnail directly, including HEIC; no UIKit/main-thread dependency.
    func thumbnail(bytes: KotlinByteArray) -> KotlinByteArray? {
        guard let source = CGImageSourceCreateWithData(bytes.data() as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: 2048
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, UTType.png.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(destination, image, nil)
        guard CGImageDestinationFinalize(destination) else { return nil }
        return (output as Data).kotlinBytes()
    }

    func pickPhoto(onResult: @escaping (PickedPhoto?, String?) -> Void) {
        guard photoResult == nil, let presenter = presenter() else {
            _ = onResult(nil, "사진 선택 화면을 열 수 없습니다.")
            return
        }
        photoResult = onResult
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 1
        configuration.preferredAssetRepresentationMode = .compatible
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        presenter.present(picker, animated: true)
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        let completion = photoResult
        photoResult = nil
        guard let provider = results.first?.itemProvider else { _ = completion?(nil, nil); return }
        guard provider.canLoadObject(ofClass: UIImage.self) else {
            _ = completion?(nil, "지원하지 않는 사진 형식입니다.")
            return
        }
        provider.loadObject(ofClass: UIImage.self) { object, error in
            let image = object as? UIImage
            // Re-encoding provides the JPEG content type accepted by the API, including HEIC sources.
            let data = image?.jpegData(compressionQuality: 0.9)
            DispatchQueue.main.async {
                guard error == nil, let data = data, !data.isEmpty else {
                    _ = completion?(nil, "사진을 읽지 못했습니다. 다시 선택해 주세요.")
                    return
                }
                guard data.count <= 5 * 1024 * 1024 else {
                    _ = completion?(nil, "사진은 5MB 이하로 선택해 주세요.")
                    return
                }
                _ = completion?(PickedPhoto(bytes: data.kotlinBytes(), filename: "photo-\(UUID().uuidString).jpg", contentType: "image/jpeg"), nil)
            }
        }
    }

    func requestLocation(onResult: @escaping (Coordinates?, String?) -> Void) {
        guard locationResult == nil else { _ = onResult(nil, "현재 위치를 확인하고 있습니다."); return }
        locationResult = onResult
        let timeout = DispatchWorkItem { [weak self] in
            self?.finishLocation(nil, "현재 위치를 가져오지 못했습니다. 주소로 검색하거나 다시 시도해 주세요.")
        }
        locationTimeout = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + 25, execute: timeout)
        updateLocationAuthorization()
    }

    private func updateLocationAuthorization() {
        guard locationResult != nil else { return }
        switch locationManager.authorizationStatus {
        case .notDetermined: locationManager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse: locationManager.requestLocation()
        case .denied, .restricted:
            finishLocation(nil, "위치 권한이 거부되었습니다. 설정에서 허용하거나 주소로 검색해 주세요.")
        @unknown default: finishLocation(nil, "위치 서비스를 사용할 수 없습니다.")
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) { updateLocationAuthorization() }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        finishLocation(Coordinates(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude), nil)
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finishLocation(nil, "현재 위치를 가져오지 못했습니다. 위치 서비스를 확인하거나 주소로 검색해 주세요.")
    }
    private func finishLocation(_ location: Coordinates?, _ error: String?) {
        locationTimeout?.cancel()
        locationTimeout = nil
        let completion = locationResult
        locationResult = nil
        _ = completion?(location, error)
    }

    func previewPdf(bytes: KotlinByteArray, filename: String, onResult: @escaping (String?) -> Void) {
        guard let document = PDFDocument(data: bytes.data()), let presenter = presenter() else {
            _ = onResult("PDF를 열지 못했습니다. 다시 다운로드해 주세요.")
            return
        }
        let controller = PDFPreviewController(document: document)
        controller.title = filename
        presenter.present(UINavigationController(rootViewController: controller), animated: true)
        _ = onResult(nil)
    }

    func sharePdf(bytes: KotlinByteArray, filename: String, onResult: @escaping (String?) -> Void) {
        do {
            let url = try pdfFile(bytes: bytes, filename: filename)
            guard let presenter = presenter() else { _ = onResult("공유 화면을 열지 못했습니다."); return }
            let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            controller.popoverPresentationController?.sourceView = presenter.view
            controller.popoverPresentationController?.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 1, height: 1)
            controller.completionWithItemsHandler = { _, completed, _, error in
                _ = onResult(error != nil ? "PDF를 공유하지 못했습니다." : (completed ? nil : "공유를 취소했습니다."))
            }
            presenter.present(controller, animated: true)
        } catch { _ = onResult("PDF 파일을 준비하지 못했습니다.") }
    }

    func savePdf(bytes: KotlinByteArray, filename: String, onResult: @escaping (String?) -> Void) {
        guard saveResult == nil else { _ = onResult("파일 저장이 이미 진행 중입니다."); return }
        do {
            let url = try pdfFile(bytes: bytes, filename: filename)
            guard let presenter = presenter() else { _ = onResult("저장 화면을 열지 못했습니다."); return }
            saveResult = onResult
            let controller = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
            controller.delegate = self
            presenter.present(controller, animated: true)
        } catch { _ = onResult("PDF 파일을 준비하지 못했습니다.") }
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        let completion = saveResult
        saveResult = nil
        _ = completion?(urls.isEmpty ? "파일을 저장하지 못했습니다." : nil)
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        let completion = saveResult
        saveResult = nil
        _ = completion?("파일 저장을 취소했습니다.")
    }

    private func pdfFile(bytes: KotlinByteArray, filename: String) throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("comparison-pdf", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let oldFiles = (try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        for file in oldFiles {
            if let date = try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate,
               Date().timeIntervalSince(date) > 86400 { try? FileManager.default.removeItem(at: file) }
        }
        let name = URL(fileURLWithPath: filename).lastPathComponent
        let url = folder.appendingPathComponent("\(UUID().uuidString)-\(name.hasSuffix(".pdf") ? name : name + ".pdf")")
        try bytes.data().write(to: url, options: [.atomic, .completeFileProtection])
        return url
    }

    func createMap() -> UIView { PropertyMapView() }
    func updateMap(view: UIView, markers: [MapMarker], selectedLocation: Coordinates?, onLocationSelected: @escaping (Coordinates) -> Void) {
        (view as? PropertyMapView)?.update(markers: markers, selectedLocation: selectedLocation, onSelection: onLocationSelected)
    }

    private func presenter() -> UIViewController? {
        var controller = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }.first { $0.isKeyWindow }?.rootViewController
        while let presented = controller?.presentedViewController { controller = presented }
        return controller
    }
}

private final class PropertyMapView: MKMapView, MKMapViewDelegate {
    private var onSelection: ((Coordinates) -> Void)?
    private var selectedCameraCoordinate: CLLocationCoordinate2D?
    private var markerSignature = ""

    init() {
        super.init(frame: .zero)
        delegate = self
        setRegion(MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780), latitudinalMeters: 5000, longitudinalMeters: 5000), animated: false)
        let recognizer = UITapGestureRecognizer(target: self, action: #selector(tapped(_:)))
        recognizer.cancelsTouchesInView = false
        addGestureRecognizer(recognizer)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func update(markers: [MapMarker], selectedLocation: Coordinates?, onSelection: @escaping (Coordinates) -> Void) {
        self.onSelection = onSelection
        let signature = markers.map { "\($0.id)|\($0.title)|\($0.latitude)|\($0.longitude)" }.joined(separator: ";")
            + "|\(selectedLocation?.latitude ?? 0)|\(selectedLocation?.longitude ?? 0)"
        if markerSignature != signature {
            markerSignature = signature
            removeAnnotations(annotations)
            for marker in markers {
                let annotation = MKPointAnnotation()
                annotation.title = marker.title
                annotation.coordinate = CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude)
                addAnnotation(annotation)
            }
            if let selected = selectedLocation {
                let annotation = MKPointAnnotation()
                annotation.title = "선택한 위치"
                annotation.coordinate = CLLocationCoordinate2D(latitude: selected.latitude, longitude: selected.longitude)
                addAnnotation(annotation)
            }
        }
        let latitude = selectedLocation?.latitude ?? markers.first?.latitude
        let longitude = selectedLocation?.longitude ?? markers.first?.longitude
        if let latitude = latitude, let longitude = longitude,
           selectedCameraCoordinate?.latitude != latitude || selectedCameraCoordinate?.longitude != longitude {
            let center = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
            selectedCameraCoordinate = center
            setCenter(center, animated: true)
        }
    }

    @objc private func tapped(_ gesture: UITapGestureRecognizer) {
        let point = gesture.location(in: self)
        if hitTest(point, with: nil) is MKAnnotationView { return }
        let coordinate = convert(point, toCoordinateFrom: self)
        _ = onSelection?(Coordinates(latitude: coordinate.latitude, longitude: coordinate.longitude))
    }
    func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
        guard let annotation = view.annotation else { return }
        _ = onSelection?(Coordinates(latitude: annotation.coordinate.latitude, longitude: annotation.coordinate.longitude))
    }
    func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
        guard !(annotation is MKUserLocation) else { return nil }
        let view = (dequeueReusableAnnotationView(withIdentifier: "property") as? MKMarkerAnnotationView)
            ?? MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: "property")
        view.annotation = annotation
        view.canShowCallout = true
        view.markerTintColor = .systemTeal
        return view
    }
}

private final class PDFPreviewController: UIViewController {
    private let document: PDFDocument
    init(document: PDFDocument) { self.document = document; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
    override func viewDidLoad() {
        super.viewDidLoad()
        let pdfView = PDFView(frame: view.bounds)
        pdfView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        pdfView.document = document
        pdfView.autoScales = true
        pdfView.backgroundColor = .systemBackground
        view.addSubview(pdfView)
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
    }
    @objc private func close() { dismiss(animated: true) }
}

private extension KotlinByteArray {
    func data() -> Data {
        Data((0..<Int(size)).map { UInt8(bitPattern: get(index: Int32($0))) })
    }
}
private extension Data {
    func kotlinBytes() -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() { array.set(index: Int32(index), value: Int8(bitPattern: byte)) }
        return array
    }
}
