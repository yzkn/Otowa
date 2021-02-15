package jp.gr.java_conf.ya.otowa


class KmlUtil() {
    companion object {
        const val KmlHeader = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2"
	xmlns:gx="http://www.google.com/kml/ext/2.2"
	xmlns:kml="http://www.opengis.net/kml/2.2"
	xmlns:atom="http://www.w3.org/2005/Atom">
	<Document>
		<Style id="route_n">
			<IconStyle>
				<Icon>
					<href>http://maps.google.com/mapfiles/kml/paddle/blu-blank.png</href>
				</Icon>
			</IconStyle>
		</Style>
		<StyleMap id="route">
			<Pair>
				<key>normal</key>
				<styleUrl>#route_n</styleUrl>
			</Pair>
			<Pair>
				<key>highlight</key>
				<styleUrl>#route_h</styleUrl>
			</Pair>
		</StyleMap>
		<Style id="route_h">
			<IconStyle>
				<scale>1.2</scale>
				<Icon>
					<href>http://maps.google.com/mapfiles/kml/paddle/blu-blank.png</href>
				</Icon>
			</IconStyle>
		</Style>
		<Folder>
			<name>Routes</name>
"""

        const val KmlFooter = """
        </Folder>
	</Document>
</kml>
"""
    }
}