package org.commcare.android.javarosa;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * @author ctsims
 */
interface DeviceReportElement {
    void writeToDeviceReport(XmlSerializer serializer) throws IOException;
}