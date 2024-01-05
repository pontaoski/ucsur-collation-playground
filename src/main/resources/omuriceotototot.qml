import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

ApplicationWindow {
    id: pissDow
    visible: true
    width: column.implicitWidth
    height: column.implicitHeight
    property alias one: field1.text
    onOneChanged: console.warn(`${one} is changed! (qml)`)
    property alias two: field2.text

    ColumnLayout {
        id: column
        x: 0
        y: 0
        spacing: 10

        TextField {
            id: field1
            onTextChanged: console.warn("waow!")
            placeholderText: "north"
            Layout.margins: 6
        }
        ComboBox {
            model: ["north", "south"]
        }
        TextField {
            id: field2
            placeholderText: "south"
            Layout.margins: 6
        }
        Label {
            text: `hallo world!!`
        }
    }
}
