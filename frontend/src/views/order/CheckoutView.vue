<script setup>
import {onMounted, ref, watch} from "vue";
import {useRouter} from "vue-router";
import {CheckoutPage} from "@/legacy/pages";
import {api} from "@/api";

const {checkout, form, result, error, submit, money} = CheckoutPage.setup();
const router = useRouter();
const GOONG_API_KEY = import.meta.env.VITE_GOONG_API_KEY || "";
const GOONG_MAP_KEY = import.meta.env.VITE_GOONG_MAP_KEY || GOONG_API_KEY;
const SHOP_LAT = 13.782967;
const SHOP_LNG = 109.219663;
const isDevMode = import.meta.env.DEV;
const goongEnabled = !!GOONG_API_KEY && !!GOONG_MAP_KEY;
const provinces = ref([]);
const wards = ref([]);
const selectedProvinceCode = ref("");
const selectedWardCode = ref("");
if (form.address === undefined || form.address === null) {
    form.address = "";
}
if (form.addressDetail === undefined || form.addressDetail === null) {
    form.addressDetail = "";
}
if (form.provinceCode === undefined || form.provinceCode === null) {
    form.provinceCode = "";
}
if (form.wardCode === undefined || form.wardCode === null) {
    form.wardCode = "";
}
if (form.shippingPhone === undefined || form.shippingPhone === null) {
    form.shippingPhone = "";
}
if (form.deliveryDistanceMeters === undefined || form.deliveryDistanceMeters === null) {
    form.deliveryDistanceMeters = "";
}
if (form.expectedDeliveryDate === undefined || form.expectedDeliveryDate === null) {
    form.expectedDeliveryDate = "";
}
if (form.expectedDeliveryLabel === undefined || form.expectedDeliveryLabel === null) {
    form.expectedDeliveryLabel = "";
}
const mapRef = ref(null);
const geocodeMessage = ref("");
const estimatedDeliveryText = ref("");
const placing = ref(false);
const addressSuggestions = ref([]);
let goong = null;
let goongMap = null;
let goongMarker = null;
let geocodeTimer = null;
let autocompleteTimer = null;
let geocodeAbort = null;
let autocompleteAbort = null;
let reverseAbort = null;
let suppressAutoGeocode = false;
let selectedPlaceId = "";
let manualAddressTyping = false;
const lastGeoWarning = ref("");
const selectedProvinceName = () => provinces.value.find((item) => item.code === selectedProvinceCode.value)?.name || "";
const selectedWardName = () => wards.value.find((item) => item.code === selectedWardCode.value)?.name || "";
const syncAddress = () => {
    const parts = [
        (form.addressDetail || "").trim(),
        selectedWardName(),
        selectedProvinceName()
    ].filter((part) => part && String(part).trim() !== "");
    form.address = parts.join(", ");
};
const loadProvinces = async () => {
    const res = await api.locations.provinces();
    provinces.value = res.data || [];
};
const loadWards = async (provinceCode) => {
    if (!provinceCode) {
        wards.value = [];
        return;
    }
    const res = await api.locations.wards(provinceCode);
    wards.value = res.data || [];
};
const onProvinceChange = async (provinceCode) => {
    selectedProvinceCode.value = provinceCode;
    form.provinceCode = provinceCode || "";
    selectedWardCode.value = "";
    form.wardCode = "";
    await loadWards(provinceCode);
    syncAddress();
    scheduleGeocode();
};
const onWardChange = (wardCode) => {
    selectedWardCode.value = wardCode;
    form.wardCode = wardCode || "";
    syncAddress();
    scheduleGeocode();
};
const normalizeAddressName = (value) => String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\b(tinh|thanh pho|tp|quan|huyen|thi xa|thi tran|phuong|xa)\b/g, " ")
    .replace(/[^a-z0-9]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
const matchByName = (source, targetName) => {
    const normalizedTarget = normalizeAddressName(targetName);
    if (!normalizedTarget) {
        return null;
    }
    return source.find((item) => {
        const normalizedName = normalizeAddressName(item.name);
        return normalizedName === normalizedTarget
            || normalizedName.includes(normalizedTarget)
            || normalizedTarget.includes(normalizedName);
    }) || null;
};
const matchByNameStrict = (source, targetName) => {
    const normalizedTarget = normalizeAddressName(targetName);
    if (!normalizedTarget) {
        return null;
    }
    return source.find((item) => normalizeAddressName(item.name) === normalizedTarget) || null;
};
const scheduleGeocode = () => {
    if (suppressAutoGeocode) {
        return;
    }
    if (geocodeTimer) {
        clearTimeout(geocodeTimer);
    }
    geocodeTimer = setTimeout(geocodeAddress, 500);
};
const scheduleAutocomplete = () => {
    if (autocompleteTimer) {
        clearTimeout(autocompleteTimer);
    }
    autocompleteTimer = setTimeout(fetchAutocompleteSuggestions, 250);
};
const parseAddressDetail = (value) => {
    const raw = String(value || "").trim();
    if (!raw) {
        return {raw: "", houseNumber: "", streetName: ""};
    }
    const match = raw.match(/^([0-9]{1,5}(?:\/[0-9]{1,4})?)(?:\s+|,)(.+)$/u);
    if (!match) {
        return {raw, houseNumber: "", streetName: raw};
    }
    return {
        raw,
        houseNumber: (match[1] || "").trim(),
        streetName: (match[2] || "").trim()
    };
};
const normalizeStreetName = (value) => normalizeAddressName(value)
    .replace(/\b(duong|dg|ngo|hem|pho|street|road)\b/g, " ")
    .replace(/\s+/g, " ")
    .trim();
const stripHouseNumberPrefix = (roadName, houseNumber) => {
    const normalizedHouseNumber = normalizeHouseNumber(houseNumber);
    const rawRoad = String(roadName || "").trim();
    if (!normalizedHouseNumber || !rawRoad) {
        return rawRoad;
    }
    const normalizedRoad = rawRoad.replace(/\s+/g, "");
    if (!normalizedRoad.toLowerCase().startsWith(normalizedHouseNumber.toLowerCase())) {
        return rawRoad;
    }
    const prefixRegex = new RegExp(`^${normalizedHouseNumber}(?:\\s+|\\b)`, "i");
    return rawRoad.replace(prefixRegex, "").trim();
};
const isLikelyStreetName = (roadName, wardName, provinceName) => {
    const road = normalizeAddressName(roadName);
    const ward = normalizeAddressName(wardName);
    const province = normalizeAddressName(provinceName);
    if (!road || road.length < 3) {
        return false;
    }
    if (ward && road === ward) {
        return false;
    }
    if (province && road === province) {
        return false;
    }
    return true;
};
const toNearbyLabel = (item, wardName, provinceName) => {
    const rawName = String(item?.name || "").trim();
    const rawAddress = String(item?.address || "").trim();
    const rawFormatted = String(item?.formatted_address || "").trim();
    const formattedFirst = rawFormatted ? rawFormatted.split(",")[0].trim() : "";
    const addressFirst = rawAddress ? rawAddress.split(",")[0].trim() : "";
    const candidates = [rawName, formattedFirst, addressFirst].filter((part) => String(part || "").trim() !== "");
    for (const candidate of candidates) {
        const normalized = normalizeAddressName(candidate);
        if (!normalized || normalized.length < 3) {
            continue;
        }
        if (wardName && isAdministrativeMatch(candidate, wardName)) {
            continue;
        }
        if (provinceName && isAdministrativeMatch(candidate, provinceName)) {
            continue;
        }
        return candidate;
    }
    return "";
};
const toNearbyAddressText = (label) => {
    const value = String(label || "").trim();
    if (!value) {
        return "";
    }
    if (/^gan\s+/i.test(normalizeAddressName(value))) {
        return value;
    }
    return `Gần ${value}`;
};
const buildAddressDetailFromReverse = (mapped, wardName, provinceName) => {
    const roadName = mapped?.roadName || "";
    const houseNumber = mapped?.houseNumber || "";
    if (isLikelyStreetName(roadName, wardName, provinceName)) {
        const detailParts = [houseNumber, roadName].filter((part) => String(part || "").trim() !== "");
        if (detailParts.length) {
            return detailParts.join(" ").trim();
        }
    }
    const nearbyText = toNearbyAddressText(mapped?.nearbyLabel || "");
    if (nearbyText) {
        return nearbyText;
    }
    return "";
};
const isResultMatchingStreet = (result, expectedStreet) => {
    if (!expectedStreet) {
        return true;
    }
    const address = result?.address || {};
    const actualStreet = address.road || address.pedestrian || address.neighbourhood || address.suburb || "";
    const expected = normalizeStreetName(expectedStreet);
    const actual = normalizeStreetName(actualStreet);
    if (!expected || !actual) {
        return false;
    }
    return actual.includes(expected) || expected.includes(actual);
};
const extractHouseNumber = (result) => String(result?.address?.house_number || "").trim();
const isInvalidHouseNumber = (value) => /^-/.test(value) || /^[0-9]{6,}/.test(value);
const normalizeHouseNumber = (value) => String(value || "").trim().replace(/\s+/g, "");
const createAbortError = () => {
    const error = new Error("AbortError");
    error.name = "AbortError";
    return error;
};
const isAdministrativeMatch = (actual, expected) => {
    const actualNormalized = normalizeAddressName(actual);
    const expectedNormalized = normalizeAddressName(expected);
    if (!actualNormalized || !expectedNormalized) {
        return false;
    }
    return actualNormalized === expectedNormalized
        || actualNormalized.includes(expectedNormalized)
        || expectedNormalized.includes(actualNormalized);
};
const toSearchableAddressText = (item) => {
    const components = Array.isArray(item?.address_components) ? item.address_components : [];
    const compound = item?.compound || {};
    return [
        item?.formatted_address,
        item?.address,
        item?.name,
        compound.commune,
        compound.district,
        compound.province,
        ...components.map((component) => component?.long_name)
    ].filter((part) => String(part || "").trim() !== "").join(" | ");
};
const matchesAdministrativeByText = (searchText, expectedName) => {
    const normalizedText = normalizeAddressName(searchText);
    const normalizedExpected = normalizeAddressName(expectedName);
    if (!normalizedText || !normalizedExpected) {
        return false;
    }
    return normalizedText.includes(normalizedExpected) || normalizedExpected.includes(normalizedText);
};
const isCandidateInArea = (item, expectedProvince, expectedWard) => {
    const provinceOk = !expectedProvince
        || isAdministrativeMatch(item.address?.state, expectedProvince)
        || matchesAdministrativeByText(item.searchText, expectedProvince);
    const wardValue = item.address?.suburb || "";
    const wardOk = !expectedWard
        || isAdministrativeMatch(wardValue, expectedWard)
        || matchesAdministrativeByText(item.searchText, expectedWard);
    return provinceOk && wardOk;
};
const typePriority = (types) => {
    const values = Array.isArray(types) ? types.map((type) => String(type || "").toLowerCase()) : [];
    if (values.includes("house_number")) {
        return 6;
    }
    if (values.includes("street_address") || values.includes("route")) {
        return 5;
    }
    if (values.includes("premise") || values.includes("establishment")) {
        return 3;
    }
    if (values.includes("company")) {
        return 2;
    }
    return 1;
};
const scoreReverseCandidate = (item, mapped) => {
    let score = typePriority(item?.types);
    if (mapped.houseNumber) {
        score += 3;
    }
    if (mapped.roadName && normalizeStreetName(mapped.roadName).length >= 3) {
        score += 2;
    }
    if (mapped.wardName) {
        score += 2;
    }
    if (mapped.provinceName) {
        score += 2;
    }
    const searchText = toSearchableAddressText(item);
    if (/\b\d{1,5}(?:\/\d{1,4})?\b/u.test(searchText)) {
        score += 1;
    }
    if (mapped.nearbyLabel) {
        score += 1;
    }
    if (!mapped.houseNumber && !mapped.roadName) {
        score -= 2;
    }
    return score;
};
const scoreCandidate = (item, parsed, expectedProvince, expectedWard) => {
    let score = 0;
    if (isCandidateInArea(item, expectedProvince, expectedWard)) {
        score += 8;
    } else {
        return -1;
    }
    if (isResultMatchingStreet(item, parsed.streetName)) {
        score += 4;
    }
    const returnedHouse = extractHouseNumber(item);
    if (parsed.houseNumber && normalizeHouseNumber(returnedHouse) === normalizeHouseNumber(parsed.houseNumber)) {
        score += 3;
    }
    if (!parsed.houseNumber && returnedHouse) {
        score += 1;
    }
    return score;
};
const toGoongAddressMap = (item) => {
    const components = Array.isArray(item?.address_components) ? item.address_components : [];
    const compound = item?.compound || {};
    const formattedAddress = String(item?.formatted_address || "");
    const rawAddress = String(item?.address || "");
    const parts = formattedAddress
        .split(",")
        .map((part) => part.trim())
        .filter((part) => part !== "");
    const provinceName = String(
        compound.province
        || parts[parts.length - 1]
        || components[components.length - 1]?.long_name
        || ""
    );
    const wardName = String(
        compound.commune
        || compound.ward
        || parts[parts.length - 3]
        || parts[parts.length - 2]
        || components[components.length - 3]?.long_name
        || components[components.length - 2]?.long_name
        || ""
    );
    const houseRoadPart = rawAddress
        ? rawAddress.split(",")[0].trim()
        : (parts[0] || "");
    const numberMatch = houseRoadPart.match(/^([0-9]{1,5}(?:\/[0-9]{1,4})?)/u);
    const houseNumber = numberMatch ? numberMatch[1] : "";
    const rawRoadName = houseRoadPart || String(components[1]?.long_name || components[0]?.long_name || "");
    const roadName = stripHouseNumberPrefix(rawRoadName, houseNumber);
    return {provinceName, wardName, roadName, houseNumber};
};
const mapGoongResultToCandidate = (item) => {
    const mapped = toGoongAddressMap(item);
    return {
        lat: String(item?.geometry?.location?.lat || ""),
        lon: String(item?.geometry?.location?.lng || ""),
        searchText: toSearchableAddressText(item),
        address: {
            road: mapped.roadName,
            house_number: mapped.houseNumber,
            state: mapped.provinceName,
            suburb: mapped.wardName
        }
    };
};
const fetchAutocompleteSuggestions = async () => {
    const input = String(form.addressDetail || "").trim();
    if (!input || input.length < 3) {
        addressSuggestions.value = [];
        return;
    }
    if (!GOONG_API_KEY) {
        addressSuggestions.value = [];
        return;
    }
    if (autocompleteAbort) {
        autocompleteAbort.abort();
    }
    autocompleteAbort = new AbortController();
    try {
        const areaHint = [selectedWardName(), selectedProvinceName()].filter((part) => String(part || "").trim() !== "").join(", ");
        const queryInput = areaHint ? `${input}, ${areaHint}` : input;
        const url = `https://rsapi.goong.io/v2/place/autocomplete?input=${encodeURIComponent(queryInput)}&api_key=${encodeURIComponent(GOONG_API_KEY)}`;
        const response = await fetch(url, {signal: autocompleteAbort.signal});
        const json = await response.json();
        const predictions = Array.isArray(json?.predictions) ? json.predictions : [];
        addressSuggestions.value = predictions.slice(0, 6).map((prediction) => ({
            placeId: String(prediction?.place_id || ""),
            description: String(prediction?.description || ""),
            mainText: String(prediction?.structured_formatting?.main_text || prediction?.description || "")
        }));
    } catch (e) {
        if (e?.name !== "AbortError") {
            addressSuggestions.value = [];
        }
    }
};
const applyPlaceById = async (placeId) => {
    if (!placeId || !GOONG_API_KEY) {
        return false;
    }
    if (geocodeAbort) {
        geocodeAbort.abort();
    }
    geocodeAbort = new AbortController();
    try {
        const url = `https://rsapi.goong.io/v2/geocode?place_id=${encodeURIComponent(placeId)}&api_key=${encodeURIComponent(GOONG_API_KEY)}`;
        const response = await fetch(url, {signal: geocodeAbort.signal});
        const json = await response.json();
        const results = Array.isArray(json?.results) ? json.results : [];
        const candidates = results
            .filter((item) => item?.geometry?.location)
            .map((item) => mapGoongResultToCandidate(item));
        if (!candidates.length) {
            return false;
        }
        const selectedProvince = selectedProvinceName();
        const selectedWard = selectedWardName();
        const inArea = candidates.filter((item) => isCandidateInArea(item, selectedProvince, selectedWard));
        const target = inArea[0] || null;
        if (!target) {
            geocodeMessage.value = "Địa chỉ gợi ý không thuộc tỉnh/phường đang chọn. Vui lòng chọn gợi ý khác.";
            return false;
        }
        return applyGeoResult([target]);
    } catch (e) {
        if (e?.name === "AbortError") {
            return false;
        }
        return false;
    }
};
const selectAddressSuggestion = async (suggestion) => {
    if (!suggestion?.placeId) {
        return;
    }
    manualAddressTyping = false;
    selectedPlaceId = suggestion.placeId;
    form.addressDetail = suggestion.mainText || suggestion.description || form.addressDetail;
    addressSuggestions.value = [];
    await applyPlaceById(selectedPlaceId);
};
const onAddressInput = () => {
    manualAddressTyping = true;
};
const onAddressEnter = async () => {
    const suggestions = [...addressSuggestions.value];
    manualAddressTyping = false;
    if (autocompleteTimer) {
        clearTimeout(autocompleteTimer);
    }
    if (autocompleteAbort) {
        autocompleteAbort.abort();
    }
    addressSuggestions.value = [];
    geocodeMessage.value = "";
    if (suggestions.length) {
        await selectAddressSuggestion(suggestions[0]);
        return;
    }
    selectedPlaceId = "";
    await geocodeAddress();
};
const searchCandidatesWithGoong = async (query, signal) => {
    if (!GOONG_API_KEY) {
        return [];
    }
    if (signal?.aborted) {
        throw createAbortError();
    }
    const url = `https://rsapi.goong.io/v2/geocode?address=${encodeURIComponent(query)}&api_key=${encodeURIComponent(GOONG_API_KEY)}`;
    const response = await fetch(url, {signal});
    const json = await response.json();
    const results = Array.isArray(json?.results) ? json.results : [];
    return results
        .filter((item) => item?.geometry?.location)
        .map((item) => {
            const mapped = toGoongAddressMap(item);
            return mapGoongResultToCandidate(item);
        });
};
const reverseWithGoong = async (lat, lng, signal) => {
    if (!GOONG_API_KEY) {
        return null;
    }
    if (signal?.aborted) {
        throw createAbortError();
    }
    const url = `https://rsapi.goong.io/v2/geocode?latlng=${encodeURIComponent(`${lat},${lng}`)}&api_key=${encodeURIComponent(GOONG_API_KEY)}`;
    const response = await fetch(url, {signal});
    const json = await response.json();
    const results = Array.isArray(json?.results) ? json.results : [];
    if (!results.length) {
        return null;
    }
    const mappedResults = [];
    for (const result of results) {
        const mapped = toGoongAddressMap(result);
        mappedResults.push({
            ...mapped,
            nearbyLabel: toNearbyLabel(result, mapped.wardName, mapped.provinceName),
            types: Array.isArray(result?.types) ? result.types : [],
            searchText: toSearchableAddressText(result)
        });
    }
    const sorted = mappedResults
        .map((item) => ({item, score: scoreReverseCandidate(item, item)}))
        .sort((a, b) => b.score - a.score);
    const best = sorted[0]?.item || null;
    if (!best) {
        return null;
    }
    return {
        provinceName: best.provinceName || "",
        wardName: best.wardName || "",
        roadName: best.roadName || "",
        houseNumber: best.houseNumber || "",
        nearbyLabel: best.nearbyLabel || "",
        mappedResults
    };
};
const ensureGoong = async () => {
    if (!GOONG_MAP_KEY) {
        throw new Error("missing-goong-key");
    }
    if (window.goongjs) {
        window.goongjs.accessToken = GOONG_MAP_KEY;
        return window.goongjs;
    }
    await new Promise((resolve, reject) => {
        const cssId = "goong-css-checkout";
        if (!document.getElementById(cssId)) {
            const link = document.createElement("link");
            link.id = cssId;
            link.rel = "stylesheet";
            link.href = "https://cdn.jsdelivr.net/npm/@goongmaps/goong-js/dist/goong-js.css";
            document.head.appendChild(link);
        }
        const existing = document.getElementById("goong-script-checkout");
        if (existing) {
            existing.addEventListener("load", resolve);
            existing.addEventListener("error", reject);
            return;
        }
        const script = document.createElement("script");
        script.id = "goong-script-checkout";
        script.src = "https://cdn.jsdelivr.net/npm/@goongmaps/goong-js/dist/goong-js.js";
        script.async = true;
        script.defer = true;
        script.onload = resolve;
        script.onerror = reject;
        document.head.appendChild(script);
    });
    window.goongjs.accessToken = GOONG_MAP_KEY;
    return window.goongjs;
};
const applyGeoResult = (json) => {
    if (!Array.isArray(json) || !json.length) {
        return false;
    }
    const lat = Number(json[0].lat);
    const lng = Number(json[0].lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
        return false;
    }
    form.lat = lat.toFixed(6);
    form.lng = lng.toFixed(6);
    geocodeMessage.value = lastGeoWarning.value || "";
    lastGeoWarning.value = "";
    if (goongMap && goong) {
        const lngLat = [lng, lat];
        if (goongMarker) {
            goongMarker.setLngLat(lngLat);
        } else {
            goongMarker = new goong.Marker().setLngLat(lngLat).addTo(goongMap);
        }
        goongMap.flyTo({center: lngLat, zoom: 17});
    }
    return true;
};
const geocodeAddress = async () => {
    if (!GOONG_API_KEY) {
        geocodeMessage.value = "Goong đang OFF. Vui lòng kiểm tra VITE_GOONG_API_KEY.";
        return;
    }
    if (!selectedWardCode.value) {
        return;
    }
    const wardName = selectedWardName();
    const provinceName = selectedProvinceName();
    const parsed = parseAddressDetail(form.addressDetail);
    if (parsed.houseNumber && isInvalidHouseNumber(parsed.houseNumber)) {
        geocodeMessage.value = "Số nhà không hợp lệ. Vui lòng nhập lại.";
        return;
    }
    if (!parsed.streetName || normalizeStreetName(parsed.streetName).length < 3) {
        geocodeMessage.value = "Vui lòng nhập đầy đủ số nhà, tên đường hợp lệ.";
        return;
    }
    const selectedProvince = selectedProvinceName();
    const selectedWard = selectedWardName();
    if (selectedPlaceId) {
        const applied = await applyPlaceById(selectedPlaceId);
        if (applied) {
            return;
        }
    }
    let hasAnyCandidate = false;
    let hasCandidateInArea = false;
    const queryList = [
        `${parsed.raw}, ${wardName}, ${provinceName}, Việt Nam`,
        `${parsed.raw}, ${selectedWard}, ${selectedProvince}, Việt Nam`,
        `${parsed.streetName}, ${wardName}, ${provinceName}, Việt Nam`,
        `${parsed.streetName}, ${selectedWard}, ${selectedProvince}, Việt Nam`,
        `${parsed.raw}, ${provinceName}, Việt Nam`,
        `${parsed.streetName}, ${provinceName}, Việt Nam`
    ];
    if (geocodeAbort) {
        geocodeAbort.abort();
    }
    geocodeAbort = new AbortController();
    try {
        for (const query of queryList) {
            const candidates = await searchCandidatesWithGoong(query, geocodeAbort.signal);
            if (!candidates.length) {
                continue;
            }
            hasAnyCandidate = true;
            const inArea = candidates.filter((item) => isCandidateInArea(item, selectedProvince, selectedWard));
            if (inArea.length) {
                hasCandidateInArea = true;
            }
            const scored = inArea
                .map((item) => ({item, score: scoreCandidate(item, parsed, selectedProvince, selectedWard)}))
                .filter((item) => item.score >= 0)
                .sort((a, b) => b.score - a.score);
            const valid = scored[0]?.item || null;
            if (!valid) {
                continue;
            }
            const exactHouse = parsed.houseNumber
                ? normalizeHouseNumber(extractHouseNumber(valid)) === normalizeHouseNumber(parsed.houseNumber)
                : true;
            const returnedHouse = extractHouseNumber(valid);
            if (parsed.houseNumber && !exactHouse) {
                lastGeoWarning.value = `Không tìm thấy chính xác số nhà ${parsed.houseNumber}, đã chọn vị trí gần nhất trên tuyến.`;
            }
            if (parsed.houseNumber && returnedHouse && normalizeHouseNumber(parsed.houseNumber) !== normalizeHouseNumber(returnedHouse)) {
                lastGeoWarning.value = `Không tìm thấy chính xác số nhà ${parsed.houseNumber}, đã chọn vị trí gần nhất trên bản đồ.`;
            }
            if (applyGeoResult([valid])) {
                return;
            }
        }
        if (hasAnyCandidate && !hasCandidateInArea) {
            geocodeMessage.value = "Địa chỉ tìm được không thuộc tỉnh/phường đã chọn. Vui lòng kiểm tra lại khu vực nhận hàng.";
            return;
        }
        geocodeMessage.value = "Goong không tìm thấy địa chỉ phù hợp với tỉnh/phường đã chọn. Vui lòng kiểm tra lại.";
    } catch (e) {
        if (e?.name === "AbortError") {
            return;
        }
        geocodeMessage.value = "Không thể định vị địa chỉ lúc này.";
    }
};
const reverseGeocodeFromMap = async (lat, lng) => {
    if (!GOONG_API_KEY) {
        geocodeMessage.value = "Goong đang OFF. Vui lòng kiểm tra VITE_GOONG_API_KEY.";
        return;
    }
    if (reverseAbort) {
        reverseAbort.abort();
    }
    reverseAbort = new AbortController();
    try {
        const mapped = await reverseWithGoong(lat, lng, reverseAbort.signal);
        if (!mapped) {
            geocodeMessage.value = "Đã cập nhật tọa độ, nhưng chưa lấy được thông tin địa chỉ từ vị trí bản đồ.";
            return;
        }
        const currentProvince = selectedProvinceName();
        const currentWard = selectedWardName();
        const provinceName = mapped?.provinceName || "";
        const wardName = mapped?.wardName || "";
        const mappedResults = Array.isArray(mapped?.mappedResults) ? mapped.mappedResults : [];
        const matchedProvince = matchByNameStrict(provinces.value, provinceName)
            || matchByName(provinces.value, provinceName)
            || provinces.value.find((province) => mappedResults.some((item) => isAdministrativeMatch(item.provinceName, province.name) || matchesAdministrativeByText(item.searchText, province.name)))
            || null;
        suppressAutoGeocode = true;
        if (matchedProvince) {
            const provinceChanged = currentProvince && !isAdministrativeMatch(matchedProvince.name, currentProvince);
            selectedProvinceCode.value = matchedProvince.code;
            form.provinceCode = matchedProvince.code;
            await loadWards(matchedProvince.code);
            const matchedWard = matchByNameStrict(wards.value, wardName)
                || matchByName(wards.value, wardName)
                || wards.value.find((ward) => mappedResults.some((item) => isAdministrativeMatch(item.wardName, ward.name) || matchesAdministrativeByText(item.searchText, ward.name)))
                || null;
            if (matchedWard) {
                selectedWardCode.value = matchedWard.code;
                form.wardCode = matchedWard.code;
            } else {
                geocodeMessage.value = "Đã cập nhật tọa độ. Không xác định chính xác phường/xã từ vị trí này, vui lòng chọn lại thủ công.";
            }
            if (provinceChanged) {
                geocodeMessage.value = "Đã cập nhật địa chỉ theo vị trí bản đồ mới.";
            }
        } else {
            geocodeMessage.value = "Đã cập nhật tọa độ. Không xác định chính xác tỉnh/thành từ vị trí này, vui lòng chọn lại thủ công.";
        }
        const detailText = buildAddressDetailFromReverse(mapped, wardName, provinceName);
        if (detailText) {
            manualAddressTyping = false;
            addressSuggestions.value = [];
            form.addressDetail = detailText;
        }
        syncAddress();
    } catch (e) {
        if (e?.name !== "AbortError") {
            geocodeMessage.value = "Đã cập nhật tọa độ, nhưng chưa đồng bộ được tỉnh/phường từ vị trí bản đồ.";
        }
    } finally {
        suppressAutoGeocode = false;
    }
};
watch(() => form.addressDetail, () => {
    syncAddress();
    scheduleGeocode();
    if (manualAddressTyping) {
        selectedPlaceId = "";
        scheduleAutocomplete();
    } else {
        addressSuggestions.value = [];
    }
    manualAddressTyping = false;
});
onMounted(async () => {
    try {
        goong = await ensureGoong();
        if (!mapRef.value) {
            return;
        }
        goongMap = new goong.Map({
            container: mapRef.value,
            style: `https://tiles.goong.io/assets/goong_map_web.json?api_key=${encodeURIComponent(GOONG_MAP_KEY)}`,
            center: [108.2772, 14.0583],
            zoom: 6
        });
        goongMap.on("error", (event) => {
            const status = event?.error?.status;
            if (status === 403) {
                geocodeMessage.value = "Goong Map key chưa được cấp quyền Tiles/Web SDK (403). Vui lòng tạo hoặc bật đúng quyền cho VITE_GOONG_MAP_KEY.";
            }
        });
        goongMap.addControl(new goong.NavigationControl(), "top-right");
        goongMap.on("click", (event) => {
            const lat = event?.lngLat?.lat;
            const lng = event?.lngLat?.lng;
            if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
                return;
            }
            const lngLat = [lng, lat];
            if (goongMarker) {
                goongMarker.setLngLat(lngLat);
            } else {
                goongMarker = new goong.Marker().setLngLat(lngLat).addTo(goongMap);
            }
            form.lat = lat.toFixed(6);
            form.lng = lng.toFixed(6);
            geocodeMessage.value = "";
            addressSuggestions.value = [];
            reverseGeocodeFromMap(lat, lng);
        });
    } catch (e) {
        geocodeMessage.value = "Không tải được Goong. Vui lòng kiểm tra lại API key.";
    }
    try {
        await loadProvinces();
        if (form.provinceCode) {
            selectedProvinceCode.value = form.provinceCode;
            await loadWards(form.provinceCode);
        } else if (provinces.value.length) {
            selectedProvinceCode.value = provinces.value[0].code;
            form.provinceCode = selectedProvinceCode.value;
            await loadWards(selectedProvinceCode.value);
        }
        if (form.wardCode) {
            selectedWardCode.value = form.wardCode;
        }
        syncAddress();
    } catch (e) {
        geocodeMessage.value = "Không tải được danh mục địa chỉ hành chính.";
    }
});
const submitCheckout = async () => {
    if (!form.provinceCode || !form.wardCode) {
        geocodeMessage.value = "Vui lòng chọn đầy đủ tỉnh/thành và phường/xã.";
        return;
    }
    const destinationLat = Number(form.lat);
    const destinationLng = Number(form.lng);
    if (!Number.isFinite(destinationLat) || !Number.isFinite(destinationLng)) {
        geocodeMessage.value = "Vui lòng chọn vị trí nhận hàng trên bản đồ để tính thời gian giao dự kiến.";
        return;
    }
    const estimate = await calculateDeliveryEstimate(destinationLat, destinationLng);
    if (!estimate) {
        geocodeMessage.value = "Không tính được quãng đường giao hàng từ Goong. Vui lòng thử lại.";
        return;
    }
    form.deliveryDistanceMeters = String(estimate.distanceMeters);
    form.expectedDeliveryDate = estimate.dateIso;
    form.expectedDeliveryLabel = estimate.label;
    estimatedDeliveryText.value = estimate.label;
    syncAddress();
    placing.value = true;
    await submit();
    placing.value = false;
    const orderId = result.value?.data?.orderId;
    const nextAction = result.value?.data?.nextAction;
    if (!orderId) {
        return;
    }
    if (nextAction === "BANK_TRANSFER" || (form.paymentMethod || "").toUpperCase() === "BANK") {
        await router.push(`/order/bank-transfer?id=${orderId}`);
        return;
    }
    await router.push(`/order/order-detail?id=${orderId}`);
};
const toDateIso = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
};
const toDateLabel = (date) => {
    const day = String(date.getDate()).padStart(2, "0");
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const year = date.getFullYear();
    return `${day}/${month}/${year}`;
};
const buildDeliveryEstimateByDistance = (distanceMeters) => {
    const km = Number(distanceMeters || 0) / 1000;
    const now = new Date();
    let daysToAdd = 0;
    if (km < 20) {
        daysToAdd = 0;
    } else if (km <= 300) {
        daysToAdd = 2;
    } else {
        daysToAdd = 3;
    }
    const expectedDate = new Date(now);
    expectedDate.setDate(now.getDate() + daysToAdd);
    const dateIso = toDateIso(expectedDate);
    const dateLabel = toDateLabel(expectedDate);
    const label = daysToAdd === 0
        ? `Trong ngày (${dateLabel})`
        : `${daysToAdd} ngày (${dateLabel})`;
    return {
        distanceMeters: Math.round(distanceMeters),
        daysToAdd,
        dateIso,
        label
    };
};
const calculateDeliveryEstimate = async (destinationLat, destinationLng) => {
    if (!GOONG_API_KEY) {
        return null;
    }
    const origin = `${SHOP_LAT},${SHOP_LNG}`;
    const destination = `${destinationLat},${destinationLng}`;
    const url = `https://rsapi.goong.io/v2/direction?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&vehicle=car&api_key=${encodeURIComponent(GOONG_API_KEY)}`;
    try {
        const response = await fetch(url);
        if (!response.ok) {
            return null;
        }
        const payload = await response.json();
        const routes = Array.isArray(payload?.routes) ? payload.routes : [];
        const firstRoute = routes[0];
        if (!firstRoute) {
            return null;
        }
        const legs = Array.isArray(firstRoute.legs) ? firstRoute.legs : [];
        const totalDistance = legs.reduce((sum, leg) => sum + Number(leg?.distance?.value || 0), 0);
        if (!Number.isFinite(totalDistance) || totalDistance <= 0) {
            return null;
        }
        return buildDeliveryEstimateByDistance(totalDistance);
    } catch (e) {
        return null;
    }
};
</script>

<template>
    <main class="checkout-page">
        <div class="container">
            <h1 class="page-title">Thanh toán đơn hàng</h1>
            
            <div v-if="error" class="status-message status-error">{{ error }}</div>
            
            <div class="checkout-layout">
                <div class="checkout-main">
                    <div class="checkout-section">
                        <h3 class="checkout-section-title">Thông tin đơn hàng</h3>
                        <div class="order-items-card">
                            <table class="order-items-table">
                                <thead>
                                    <tr>
                                        <th>Sản phẩm</th>
                                        <th>Giá</th>
                                        <th>SL</th>
                                        <th>Size</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr v-for="item in checkout.items" :key="item.productId + '-' + item.sizeId">
                                        <td>{{ item.name }}</td>
                                        <td>{{ money(item.price) }} VNĐ</td>
                                        <td>{{ item.quantity }}</td>
                                        <td>{{ item.sizeName }}</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                    
                    <form class="checkout-section" @submit.prevent="submitCheckout">
                        <h3 class="checkout-section-title">Địa chỉ giao hàng</h3>
                        <div class="form-group">
                            <label>Số điện thoại giao hàng</label>
                            <input v-model="form.shippingPhone" class="form-control" placeholder="Nhập số điện thoại nhận hàng" required>
                        </div>
                        
                        <div class="form-group">
                            <label>Tỉnh / Thành phố</label>
                            <select class="form-control" v-model="selectedProvinceCode" @change="onProvinceChange($event.target.value)" required>
                                <option value="">Chọn tỉnh/thành</option>
                                <option v-for="province in provinces" :key="province.code" :value="province.code">{{ province.name }}</option>
                            </select>
                        </div>
                        
                        <div class="form-group">
                            <label>Phường / Xã</label>
                            <select v-model="selectedWardCode" @change="onWardChange($event.target.value)" required class="form-control">
                                <option value="">Chọn phường/xã</option>
                                <option v-for="ward in wards" :key="ward.code" :value="ward.code">{{ ward.name }}</option>
                            </select>
                        </div>
                        
                        <div class="form-group">
                            <label>Số nhà, tên đường</label>
                            <input v-model="form.addressDetail" class="form-control" placeholder="Ví dụ: 123 Lê Lợi" required @input="onAddressInput" @keydown.enter.prevent="onAddressEnter">
                            <div v-if="addressSuggestions.length" class="order-address-suggestions">
                                <button
                                    v-for="suggestion in addressSuggestions"
                                    :key="suggestion.placeId"
                                    type="button"
                                    class="order-address-suggestion-item"
                                    @click="selectAddressSuggestion(suggestion)"
                                >
                                    {{ suggestion.description }}
                                </button>
                            </div>
                        </div>
                        
                        <div class="checkout-map" ref="mapRef"></div>
                        <div v-if="isDevMode" class="status-message" :class="goongEnabled ? 'status-success' : 'status-error'">
                            Goong: {{ goongEnabled ? "ON" : "OFF" }}
                        </div>
                        
                        <div v-if="geocodeMessage" class="status-message status-error">{{ geocodeMessage }}</div>
                        
                        <div class="form-row">
                            <div class="form-group">
                                <label>Latitude</label>
                                <input v-model="form.lat" class="form-control" readonly>
                            </div>
                            <div class="form-group">
                                <label>Longitude</label>
                                <input v-model="form.lng" class="form-control" readonly>
                            </div>
                        </div>
                        
                        <div class="checkout-payment-group">
                            <label>Hình thức thanh toán</label>
                            <div class="checkout-payment-options">
                                <label class="checkout-payment-option">
                                    <input type="radio" value="BANK" v-model="form.paymentMethod" class="checkout-payment-radio">
                                    <span>Chuyển khoản ngân hàng</span>
                                </label>
                                <label class="checkout-payment-option">
                                    <input type="radio" value="COD" v-model="form.paymentMethod" class="checkout-payment-radio">
                                    <span>Thanh toán khi nhận hàng (COD)</span>
                                </label>
                            </div>
                        </div>
                        
                        <button class="btn btn-primary btn--block" type="submit" :disabled="placing">{{ placing ? "Đang xử lý..." : "Đặt hàng ngay" }}</button>
                    </form>
                    
                    <div v-if="result" class="status-message status-success">
                        Đặt hàng thành công! Mã đơn hàng: {{ result.data?.orderId }}
                    </div>
                </div>
                
                <div class="checkout-sidebar">
                    <div class="order-summary-card">
                        <h3 class="order-summary-title">Tổng đơn hàng</h3>
                        <div class="order-summary-row">
                            <span>Tạm tính:</span>
                            <strong>{{ money(checkout.totalPrice) }} VNĐ</strong>
                        </div>
                        <div class="order-summary-row">
                            <span>Phí vận chuyển:</span>
                            <strong>Miễn phí</strong>
                        </div>
                        <div class="order-summary-row">
                            <span>Dự kiến nhận hàng:</span>
                            <strong>{{ estimatedDeliveryText || "Sẽ tính khi bấm Đặt hàng ngay" }}</strong>
                        </div>
                        <div class="order-summary-divider"></div>
                        <div class="order-summary-total">
                            <span>Tổng cộng:</span>
                            <strong>{{ money(checkout.totalPrice) }} VNĐ</strong>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </main>
</template>

<style scoped>
.order-address-suggestions {
    margin-top: 8px;
    border: 1px solid #d9d9d9;
    border-radius: 8px;
    overflow: hidden;
    background: #fff;
}

.order-address-suggestion-item {
    width: 100%;
    display: block;
    text-align: left;
    padding: 10px 12px;
    border: none;
    border-top: 1px solid #f0f0f0;
    background: #fff;
    cursor: pointer;
}

.order-address-suggestion-item:first-child {
    border-top: none;
}

.order-address-suggestion-item:hover {
    background: #f7f7f7;
}
</style>
