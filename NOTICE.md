# Notices

This application is not an official product of the Japan Meteorological Agency, Geospatial Information Authority of Japan, Open-Meteo, Weathernews, Yahoo Japan, tenki.jp, or Leaflet.

## Application code

The application code in this repository is licensed under the MIT License. See [LICENSE](LICENSE).

## Weather and disaster information

### Japan Meteorological Agency

This application uses or links to data and pages from the Japan Meteorological Agency, including weather warnings/advisories, earthquake information, tsunami information, and High-resolution Precipitation Nowcasts.

Source: Japan Meteorological Agency  
Website: https://www.jma.go.jp/

When redistributing screenshots, derived materials, or public deployments, follow the Japan Meteorological Agency website terms and include appropriate source attribution. If the data is processed or reformatted, indicate that the display was created by processing source data.

### Geospatial Information Authority of Japan

This application uses GSI Tiles, address search, and reverse geocoding services from the Geospatial Information Authority of Japan.

Source: Geospatial Information Authority of Japan  
Website: https://www.gsi.go.jp/  
GSI Tiles: https://maps.gsi.go.jp/development/ichiran.html

Map displays should include source attribution such as "地図: 国土地理院" or equivalent attribution required by the applicable GSI terms.

### Open-Meteo

This application uses the Open-Meteo Forecast API for forecast and current weather data.

Source: Open-Meteo  
Website: https://open-meteo.com/  
Terms: https://open-meteo.com/en/terms

Open-Meteo has usage limits and separate requirements for commercial or high-volume use. Review the current terms before commercial distribution, advertising-supported publication, or large-scale deployment.

### Weathernews WxTech Open Data

This application uses Weathernews WxTech Open Data for pollen information.

Source: Weathernews WxTech Open Data  
Website: https://wxtech.weathernews.com/opendata/

Review the current Weathernews WxTech Open Data terms before public or commercial distribution.

### External links

This application links to Weathernews, Yahoo天気, tenki.jp, JMA, and related official pages. These external services and trademarks belong to their respective owners. Do not imply endorsement or official affiliation.

## Map library

### Leaflet

This application loads Leaflet from the unpkg CDN.

Project: Leaflet  
Website: https://leafletjs.com/  
License: BSD 2-Clause License  
License text: https://github.com/Leaflet/Leaflet/blob/main/LICENSE

Leaflet's license notice should be preserved when redistributing bundled copies or derived distributions.

## Privacy note

When the current-location feature is enabled, the browser obtains latitude and longitude from the user's device. The application stores settings and location-related values in browser `localStorage`.

The application does not send location data to an app-specific backend, but location data may be sent directly from the browser to external APIs such as Open-Meteo and the Geospatial Information Authority of Japan to retrieve weather and area data.
