//TODO: editbox for number of races/time for race must not accept non numbers
//TODO: search for TODO

using System;
using System.Collections.Generic;
using System.Linq;
using System.IO.Ports;
using System.Text;
using System.Threading.Tasks;
using System.Collections.ObjectModel;
using System.Windows.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Shapes;
using System.Timers;
using System.Xml.Serialization;
using System.IO;

namespace chorusgui
{
    /// <summary>
    /// Interaction logic for ChorusGUI.xaml
    /// </summary>

    public class ChorusDeviceClass
    {
        public ComboBox Band;
        public ComboBox Channel;
        public CheckBox SoundState;
        public CheckBox SkipFirstLap;
        public CheckBox Calibrated;
        public int CalibrationTime;
        public Label CalibrationTimeLabel;
        public int MinimalLapTime;
        public Label MinimalLapTimeLabel;
        public CheckBox Configured;
        public CheckBox RaceActive;
        public int CurrentRSSIValue;
        public Label CurrentRSSIValueLabel;
        public int CurrentTreshold;
        public Label CurrentTresholdLabel;
        public CheckBox RSSIMonitoringActive;
        public ListView LapTimes;
        public double CurrentVoltage;
        public double BatteryVoltageAdjustment;
        public Label CurrentVoltageLabel;
        public Grid grid;
    }

    [Serializable]
    public class Pilot
    {
        private string _guid;
        public string guid {
            get {
                if (_guid == null)
                    _guid = Convert.ToBase64String(Guid.NewGuid().ToByteArray()).Substring(0,22);
                return _guid;
            }
            set {
                _guid = guid;
            }
        }
        public string Ranking { get; set; }
        public string Name { get; set; }
        public string BestLap { get; set; }
        public string BestRace { get; set; }
    }

    [Serializable]
    public class Settings
    {
        public int SerialBaud { get; set; }
        public string SerialPortName { get; set; }
        public int SerialBaudIndex { get; set; }
        public int MinimalLapTime { get; set; }
        public int QualificationRuns { get; set; }
        public int TimeToPrepare { get; set; }
        public Boolean SkipFirstLap { get; set; }
        public Boolean DoubleOut { get; set; }
        public Boolean RaceMode { get; set; }
        public int NumberofTime { get; set; }
        public Boolean VoltageMonitoring { get; set; }
        public int VoltageMonitorDevice { get; set; }
        public int NumberOfContendersForQualification { get; set; }
        public int QualificationRaces { get; set; }
        public int NumberOfContendersForRace { get; set; }
    }

    [Serializable]
    public class PilotCollection : ObservableCollection<Pilot>
    {
    }

    public partial class ChorusGUI : Window
    {
        System.Timers.Timer aTimer = new System.Timers.Timer();
        System.Timers.Timer VoltageMonitorTimer = new System.Timers.Timer();
        SerialPort mySerialPort;
        string readbuffer;
        int TimerCalibration = 1000;
        int DeviceCount;
        Boolean IsRaceActive;

        private PilotCollection Pilots;
        public Settings settings = new Settings();

        ChorusDeviceClass[] ChorusDevices;

        void LoadSettings()
        {
            XmlSerializer serializer = new XmlSerializer(typeof(Settings));
            try
            {
                using (FileStream stream = new FileStream("settings.xml", FileMode.Open))
                {
                    settings = (Settings)serializer.Deserialize(stream);
                }
            }
            catch (FileNotFoundException) {
                settings.SerialBaudIndex = 2;
                settings.MinimalLapTime = 5;
                settings.QualificationRuns = 1;
                settings.TimeToPrepare = 5;
                //TODO more default settings???
            }
            DeviceCount = 0;
            readbuffer = "";
            MinimalLapTimeLabel.Content = settings.MinimalLapTime + " seconds";
            TimeToPrepareLabel.Content = settings.TimeToPrepare + " seconds";
            IsRaceActive = false;
        }

        public ChorusGUI()
        {
            InitializeComponent();
            Title = "Chorus Lap Timer @ " + settings.SerialPortName + "(" + settings.SerialBaud + " Baud)";
            aTimer.Elapsed += new ElapsedEventHandler(OnTimedEvent);
            VoltageMonitorTimer.Elapsed += new ElapsedEventHandler(VoltageMonitorTimerEvent);
            LoadSettings();
            QualificationRunsLabel.Content = settings.QualificationRuns;
            Pilots = (PilotCollection)Resources["PilotCollection"];
            XmlSerializer serializer = new XmlSerializer(typeof(PilotCollection));
            try {
                using (FileStream stream = new FileStream("pilots.xml", FileMode.Open))
                {
                    IEnumerable<Pilot> PilotData = (IEnumerable<Pilot>)serializer.Deserialize(stream);
                    foreach (Pilot p in PilotData)
                    {
                        Pilots.Add(p);
                    }
                }
            }
            catch (FileNotFoundException) { }
        }

        private void Window_Closing(object sender, System.ComponentModel.CancelEventArgs e)
        {
            XmlSerializer serializer = new XmlSerializer(typeof(PilotCollection));
            using (FileStream stream = new FileStream("pilots.xml", FileMode.Create))
            {
                serializer.Serialize(stream, Pilots);
            }
            serializer = new XmlSerializer(typeof(Settings));
            using (FileStream stream = new FileStream("settings.xml", FileMode.Create))
            {
                serializer.Serialize(stream, settings);
            }
        }

        void Window_Loaded(object sender, RoutedEventArgs e)
        {
            mySerialPort = new SerialPort(settings.SerialPortName, settings.SerialBaud, 0, 8, StopBits.One);
            mySerialPort.DataReceived += new SerialDataReceivedEventHandler(DataReceivedHandler);
            mySerialPort.Open();
            SendData("N0");
        }

        private void textBox_OnKeyDownHandler(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Return)
            {
                SendData(textBox.Text);
                textBox.Text = "";
            }
        }

        #region Recieving

        private delegate void UpdateUiTextDelegate(string text);
        private void DataReceivedHandler(object sender, System.IO.Ports.SerialDataReceivedEventArgs e)
        {
            string recieved_data = mySerialPort.ReadExisting();
            Dispatcher.Invoke(DispatcherPriority.Send, new UpdateUiTextDelegate(ReadData), recieved_data);
        }

        private void SendData(string outdata)
        {
            mySerialPort.Write(outdata + "\n");
            listBox.Items.Add("[TX " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.ffff") + "] " + outdata);
            //TODO AUTOSCROLL???
        }

        private void OnTimedEvent(object source, ElapsedEventArgs e)
        {
            Dispatcher.Invoke(DispatcherPriority.Send, new UpdateUiTextDelegate(SendData), "R*i");
            aTimer.Stop();
        }


        private void btn_MinimalLapTime(object sender, RoutedEventArgs e)
        {
            Button button = (Button)sender;
            if (button.Name[0] == 'D')
            {
                if (settings.MinimalLapTime > 0)
                    settings.MinimalLapTime--;
            }
            else if (button.Name[0] == 'I')
            {
                if (settings.MinimalLapTime < 250)
                    settings.MinimalLapTime++;
            }
            SendData("R*L"+ settings.MinimalLapTime.ToString("X2"));
            MinimalLapTimeLabel.Content = settings.MinimalLapTime + " seconds";
        }

        private void btn_QualificationRuns(object sender, RoutedEventArgs e)
        {
            Button button = (Button)sender;
            if (button.Name[0] == 'D')
            {
                if (settings.QualificationRuns > 1)
                    settings.QualificationRuns--;
            }
            else if (button.Name[0] == 'I')
            {
                if (settings.QualificationRuns < 10)
                    settings.QualificationRuns++;
            }
            QualificationRunsLabel.Content = settings.QualificationRuns;
        }

        private void btn_TimeToPrepare(object sender, RoutedEventArgs e)
        {
            Button button = (Button)sender;
            if (button.Name[0] == 'D')
            {
                if (settings.TimeToPrepare > 0)
                    settings.TimeToPrepare--;
            }
            else if (button.Name[0] == 'I')
            {
                if (settings.TimeToPrepare < 120)
                    settings.TimeToPrepare++;
            }
            TimeToPrepareLabel.Content = settings.TimeToPrepare + " seconds";
        }

        private void device_btnClick(object sender, RoutedEventArgs e)
        {
            Button button = (Button)sender;
            var device = button.Name[2] - '0';
            switch (button.Name[3])
            {
                case 'T':
                    if (button.Name[4] == 'i')
                    {
                        SendData("R" + device + "T");
                    }
                    else if (button.Name[4] == 'd')
                    {
                        SendData("R" + device + "t");
                    }
                    else if (button.Name[4] == 's')
                    {
                        SendData("R" + device + "S");
                    }
                    break;
                case 'Y':
                    if (button.Name[4] == 'i')
                    {
                        ChorusDevices[device].BatteryVoltageAdjustment++;
                        SendData("R" + device + "Y");
                    }
                    else if (button.Name[4] == 'd')
                    {
                        ChorusDevices[device].BatteryVoltageAdjustment--; 
                        SendData("R" + device + "Y");
                    }
                    break;
            }
        }

        private void device_cbClicked(object sender, RoutedEventArgs e)
        {
            CheckBox checkbox = (CheckBox)sender;
            var device = checkbox.Name[2] - '0';
            switch (checkbox.Name[3])
            {
                case 'D':
                    SendData("R" + device + "D");
                    break;
                case 'V':
                    if (checkbox.IsChecked.Value)
                        SendData("R" + device + "V");
                    else
                        SendData("R" + device + "v");
                    break;
            }
        }

        private void device_cbSelChange(object sender, SelectionChangedEventArgs e)
        {
            ComboBox combobox = (ComboBox)sender;
            var device = combobox.Name[2] - '0';
            switch (combobox.Name[3])
            {
                case 'B':
                    SendData("R" + device + "N" + combobox.SelectedIndex);
                    break;
                case 'C':
                    SendData("R" + device + "H" + combobox.SelectedIndex);
                    break;
            }
        }

        private void ReadData(string indata)
        {
            for (int i = 0; i < indata.Length; i++)
            {
                if (indata[i] == '\n')
                {
                    if (readbuffer.Length == 0)
                        return;
                    listBox.Items.Add("[RX " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.ffff") + "] " + readbuffer);
                    //TODO AUTOSCROLL???
                    switch (readbuffer[0])
                    {
                        case 'N':
                            //TODO VERIFY SETTINGS!!!
                            if (readbuffer.Length < 2)
                                break;
                            DeviceCount = readbuffer[1] - '0';
                            if (DeviceCount == 0)
                                break;
                            contender_slider1.Maximum = DeviceCount;
                            contender_slider2.Maximum = DeviceCount;
                            ChorusDevices = new ChorusDeviceClass[DeviceCount];
                            for (int ii = 0; ii < DeviceCount; ii++)
                            {
                                cbVoltageMonitoring.Items.Add("Device " + ii);
                                ChorusDevices[ii] = new ChorusDeviceClass();
                                ChorusDevices[ii].BatteryVoltageAdjustment = 1;
                                Grid grid = new Grid();
                                CheckBox checkbox = new CheckBox();
                                checkbox.Content = "Device is configured";
                                checkbox.Name = "ID" + ii + "P";
                                checkbox.Margin = new Thickness(10, 10, 0, 0);
                                checkbox.IsEnabled = false;
                                grid.Children.Add(checkbox);
                                ChorusDevices[ii].Configured = checkbox;

                                checkbox = new CheckBox();
                                checkbox.Content = "Enable Device Sounds";
                                checkbox.Name = "ID" + ii + "D";
                                checkbox.Margin = new Thickness(200, 10, 0, 0);
                                checkbox.Click += device_cbClicked;
                                grid.Children.Add(checkbox);
                                ChorusDevices[ii].SoundState = checkbox;

                                checkbox = new CheckBox();
                                checkbox.Content = "Device is calibrated";
                                checkbox.Name = "ID" + ii + "i";
                                checkbox.IsEnabled = false;
                                checkbox.Margin = new Thickness(10, 30, 0, 0);
                                grid.Children.Add(checkbox);
                                ChorusDevices[ii].Calibrated = checkbox;

                                Label label = new Label();
                                label.Content = "Calibration Time: 0";
                                label.Name = "ID" + ii + "I";
                                label.Margin = new Thickness(200, 25, 0, 0);
                                grid.Children.Add(label);
                                ChorusDevices[ii].CalibrationTimeLabel = label;

                                checkbox = new CheckBox();
                                checkbox.Content = "Race is Active";
                                checkbox.Name = "ID" + ii + "R";
                                checkbox.IsEnabled = false;
                                checkbox.Margin = new Thickness(10, 50, 0, 0);
                                grid.Children.Add(checkbox);
                                ChorusDevices[ii].RaceActive = checkbox;

                                label = new Label();
                                label.Content = "Minimal Lap time: 0 seconds";
                                label.Name = "ID" + ii + "M";
                                label.Margin = new Thickness(200, 45, 0, 0);
                                grid.Children.Add(label);
                                ChorusDevices[ii].MinimalLapTimeLabel = label;

                                checkbox = new CheckBox();
                                checkbox.Content = "Skip First Lap";
                                checkbox.Name = "ID" + ii + "F";
                                checkbox.IsEnabled = false;
                                checkbox.Margin = new Thickness(10, 70, 0, 0);
                                grid.Children.Add(checkbox);
                                ChorusDevices[ii].SkipFirstLap = checkbox;

                                ComboBox combobox = new ComboBox();
                                combobox.Items.Add("0, Raceband");
                                combobox.Items.Add("1, Band A");
                                combobox.Items.Add("2, Band B");
                                combobox.Items.Add("3, Band E");
                                combobox.Items.Add("4, Band F(Airwave)");
                                combobox.Items.Add("5, Band D (5.3)");
                                combobox.HorizontalAlignment = HorizontalAlignment.Left;
                                combobox.VerticalAlignment = VerticalAlignment.Top;
                                combobox.SelectedIndex = 0;
                                combobox.Name = "ID" + ii + "B";
                                combobox.Margin = new Thickness(10, 90, 10, 0);
                                combobox.Height = 20;
                                combobox.Width = 150;
                                combobox.SelectionChanged += device_cbSelChange;
                                grid.Children.Add(combobox);
                                ChorusDevices[ii].Band = combobox;

                                combobox = new ComboBox();
                                combobox.Items.Add("Channel 1");
                                combobox.Items.Add("Channel 2");
                                combobox.Items.Add("Channel 3");
                                combobox.Items.Add("Channel 4");
                                combobox.Items.Add("Channel 5");
                                combobox.Items.Add("Channel 6");
                                combobox.Items.Add("Channel 7");
                                combobox.Items.Add("Channel 8");
                                combobox.HorizontalAlignment = HorizontalAlignment.Left;
                                combobox.VerticalAlignment = VerticalAlignment.Top;
                                combobox.SelectedIndex = 0;
                                combobox.Name = "ID" + ii + "C";
                                combobox.Margin = new Thickness(200, 90, 10, 0);
                                combobox.Height = 20;
                                combobox.Width = 150;
                                combobox.SelectionChanged += device_cbSelChange;
                                grid.Children.Add(combobox);
                                ChorusDevices[ii].Channel = combobox;

                                checkbox = new CheckBox();
                                checkbox.Content = "RSSI Monitoring is Active";
                                checkbox.Name = "ID" + ii + "V";
                                checkbox.Margin = new Thickness(10, 115, 0, 0);
                                checkbox.Click += device_cbClicked;
                                grid.Children.Add(checkbox);
                                ChorusDevices[ii].RSSIMonitoringActive = checkbox;

                                label = new Label();
                                label.Content = "RSSI Value: 0";
                                label.Name = "ID" + ii + "S";
                                label.Margin = new Thickness(200, 110, 0, 0);
                                grid.Children.Add(label);
                                ChorusDevices[ii].CurrentRSSIValueLabel = label;

                                label = new Label();
                                label.Content = "Current RSSI Treshold: 0";
                                label.Name = "ID" + ii + "T";
                                label.Margin = new Thickness(10, 132, 0, 0);
                                grid.Children.Add(label);
                                ChorusDevices[ii].CurrentTresholdLabel = label;

                                Button button = new Button();
                                button.Name = "ID" + ii + "Td";
                                button.Content = "-";
                                button.Margin = new Thickness(200, 135, 0, 0);
                                button.Height = 20;
                                button.Width = 20;
                                button.HorizontalAlignment = HorizontalAlignment.Left;
                                button.VerticalAlignment = VerticalAlignment.Top;
                                button.Click += device_btnClick;
                                grid.Children.Add(button);

                                button = new Button();
                                button.Name = "ID" + ii + "Ts";
                                button.Content = "Set";
                                button.Margin = new Thickness(230, 135, 0, 0);
                                button.Height = 20;
                                button.Width = 50;
                                button.HorizontalAlignment = HorizontalAlignment.Left;
                                button.VerticalAlignment = VerticalAlignment.Top;
                                button.Click += device_btnClick;
                                grid.Children.Add(button);

                                button = new Button();
                                button.Name = "ID" + ii + "Ti";
                                button.Content = "+";
                                button.Margin = new Thickness(290, 135, 0, 0);
                                button.Height = 20;
                                button.Width = 20;
                                button.HorizontalAlignment = HorizontalAlignment.Left;
                                button.VerticalAlignment = VerticalAlignment.Top;
                                button.Click += device_btnClick;
                                grid.Children.Add(button);

                                label = new Label();
                                label.Content = "Current Voltage: X.XX";
                                label.Name = "ID" + ii + "Y";
                                label.Margin = new Thickness(10, 162, 0, 0);
                                grid.Children.Add(label);
                                ChorusDevices[ii].CurrentVoltageLabel = label;

                                button = new Button();
                                button.Name = "ID" + ii + "Yd";
                                button.Content = "-";
                                button.Margin = new Thickness(200, 165, 0, 0);
                                button.Height = 20;
                                button.Width = 20;
                                button.HorizontalAlignment = HorizontalAlignment.Left;
                                button.VerticalAlignment = VerticalAlignment.Top;
                                button.Click += device_btnClick;
                                grid.Children.Add(button);

                                button = new Button();
                                button.Name = "ID" + ii + "Yi";
                                button.Content = "+";
                                button.Margin = new Thickness(290, 165, 0, 0);
                                button.Height = 20;
                                button.Width = 20;
                                button.HorizontalAlignment = HorizontalAlignment.Left;
                                button.VerticalAlignment = VerticalAlignment.Top;
                                button.Click += device_btnClick;
                                grid.Children.Add(button);

                                label = new Label();
                                label.Content = "Laps since last Race:";
                                label.Name = "ID" + ii + "Llabel";
                                label.Margin = new Thickness(10, 185, 0, 0);
                                grid.Children.Add(label);

                                ListView listview = new ListView();
                                listview.Name = "ID" + ii + "L";
                                listview.Margin = new Thickness(10, 210, 10, 10);

                                GridView myGridView = new GridView();
                                myGridView.AllowsColumnReorder = true;
                                GridViewColumn gvc1 = new GridViewColumn();
                                gvc1.DisplayMemberBinding = new Binding("Lap");
                                gvc1.Header = "Lap";
                                gvc1.Width = 50;
                                myGridView.Columns.Add(gvc1);
                                GridViewColumn gvc2 = new GridViewColumn();
                                gvc2.DisplayMemberBinding = new Binding("Time");
                                gvc2.Header = "Milliseconds";
                                gvc2.Width = 300;
                                myGridView.Columns.Add(gvc2);
                                listview.View = myGridView;
                                grid.Children.Add(listview);
                                ChorusDevices[ii].LapTimes = listview;

                                TabItem ti = new TabItem();
                                ti.Header = "Device " + ii;
                                ti.Name = "DEVICE" + ii;
                                ti.Content = grid;
                                Settings_TabControl.Items.Add(ti);
                                ChorusDevices[ii].grid = grid;
                            }
                            cbVoltageMonitoring.SelectedIndex = 0;
                            SendData("R*L" + settings.MinimalLapTime.ToString("X2"));
                            //TODO CONFIG DEVICES WITH DEFAULT VALUES??? -> KEEP IN MIND R*A IS CAUSING PROBLEMS!!!
                            SendData("R"+(DeviceCount-1)+"A");
                            break;
                        case 'S':
                            if (readbuffer.Length < 4)
                                break;
                            int device = readbuffer[1] - '0';
                            switch (readbuffer[2])
                            {
                                case 'B': //Current Band (half-byte; 0 - 5)
                                    ChorusDevices[device].Band.SelectedIndex = readbuffer[3] - '0';
                                    break;
                                case 'C': //Current Channel (half-byte; 0 - 7)
                                    ChorusDevices[device].Channel.SelectedIndex = readbuffer[3] - '0';
                                    break;
                                case 'D': //Sound State (half-byte; 1 = On, 0 = Off)
                                    if (readbuffer[3] == '0')
                                        ChorusDevices[device].SoundState.IsChecked = false;
                                    else
                                        ChorusDevices[device].SoundState.IsChecked = true;
                                    break;
                                case 'F': //First Lap State (half-byte; 1 = Skip, 0 = Count)
                                    if (readbuffer[3] == '0')
                                        ChorusDevices[device].SkipFirstLap.IsChecked = false;
                                    else
                                        ChorusDevices[device].SkipFirstLap.IsChecked = true;
                                    if (cbSkipFirstLap.IsChecked.Value != ChorusDevices[device].SkipFirstLap.IsChecked.Value)
                                    {
                                        SendData("R"+device+"F");
                                    }
                                    break;
                                case 'i': //Calibration State (half-byte, 1 = Calibrated, 0 = Not Calibrated)
                                    if (readbuffer[3] == '0')
                                        ChorusDevices[device].Calibrated.IsChecked = false;
                                    else
                                    {
                                        ChorusDevices[device].Calibrated.IsChecked = true;
                                        if (device<8)
                                            SendData("R" + device + "H" + device);
                                    }
                                    break;
                                case 'I': //Calibration Time (4 bytes)
                                    //TODO WEIRD RESULTS???
                                    //ChorusDevices[device].CalibrationTime = int.Parse(readbuffer.Substring(3), System.Globalization.NumberStyles.HexNumber);
                                    //SendData("C"+device+(ChorusDevices[device].CalibrationTime - TimerCalibration).ToString("X1"));
                                    ChorusDevices[device].CalibrationTime = TimerCalibration;
                                    SendData("C" + device + "0");
                                    ChorusDevices[device].CalibrationTimeLabel.Content = "Calibration Time: " + ChorusDevices[device].CalibrationTime + " ms for " + TimerCalibration + " ms";
                                    break;
                                case 'L': //Lap Time; last Lap Time is automatically sent in race mode when drone passes the gate; All Lap Times sent as a response to Bulk Device State (see below); Format: (1 byte: lap number + 4 bytes: lap time)
                                    TriggerLap(device, int.Parse(readbuffer.Substring(3, 2), System.Globalization.NumberStyles.HexNumber), int.Parse(readbuffer.Substring(5), System.Globalization.NumberStyles.HexNumber));
                                    break;
                                case 'M': //Minimal Lap Time (1 byte, in seconds)
                                    ChorusDevices[device].MinimalLapTime = int.Parse(readbuffer.Substring(3), System.Globalization.NumberStyles.HexNumber);
                                    ChorusDevices[device].MinimalLapTimeLabel.Content = "Minimal Lap time: " + ChorusDevices[device].MinimalLapTime + " seconds";
                                    if (ChorusDevices[device].MinimalLapTime != settings.MinimalLapTime)
                                    {
                                        SendData("R" + device + "N" + settings.MinimalLapTime.ToString("X2"));
                                    }
                                    break;
                                case 'P': //Device ist configured (half-byte, 1 = yes, 0 = no)
                                    if (readbuffer[3] == '0')
                                        ChorusDevices[device].Configured.IsChecked = false;
                                    else
                                        ChorusDevices[device].Configured.IsChecked = true;
                                    break;
                                case 'R': //Race Status (half-byte; 1 = On, 0 = Off)
                                    if (readbuffer[3] == '0')
                                    {
                                        ChorusDevices[device].RaceActive.IsChecked = false;
                                    }
                                    else
                                    {
                                        ChorusDevices[device].RaceActive.IsChecked = true;
                                        ChorusDevices[device].LapTimes.Items.Clear();
                                    }
                                    break;
                                case 'S': //Current RSSI Value; sent each 100ms when RSSI Monitor is On (2 bytes)
                                    ChorusDevices[device].CurrentRSSIValue = int.Parse(readbuffer.Substring(3), System.Globalization.NumberStyles.HexNumber);
                                    ChorusDevices[device].CurrentRSSIValueLabel.Content = "RSSI Value: " + ChorusDevices[device].CurrentRSSIValue;
                                    break;
                                case 'T': //Current Threshold (2 bytes)
                                    ChorusDevices[device].CurrentTreshold = int.Parse(readbuffer.Substring(3), System.Globalization.NumberStyles.HexNumber);
                                    ChorusDevices[device].CurrentTresholdLabel.Content = "Current RSSI Treshold: " + ChorusDevices[device].CurrentTreshold;
                                    break;
                                case 'V': //RSSI Monitor State (half-byte; 1 = On, 0 = Off)
                                    if (readbuffer[3] == '0')
                                        ChorusDevices[device].RSSIMonitoringActive.IsChecked = false;
                                    else
                                        ChorusDevices[device].RSSIMonitoringActive.IsChecked = true;
                                    break;
                                case 'Y': //Current Voltage (2 bytes)
                                    ChorusDevices[device].CurrentVoltage = int.Parse(readbuffer.Substring(3), System.Globalization.NumberStyles.HexNumber);
                                    double batteryVoltage = (double)ChorusDevices[device].CurrentVoltage * 11 * 5 * (((double)ChorusDevices[device].BatteryVoltageAdjustment + 1000) / 1000) / 1024;
                                    int cellsCount = (int)(batteryVoltage / 3.4);
                                    double cellVoltage = batteryVoltage / cellsCount;
                                    ChorusDevices[device].CurrentVoltageLabel.Content = "Current Cell Voltage: " + cellVoltage.ToString("0.00") + " Volt";
                                    if (device == cbVoltageMonitoring.SelectedIndex) 
                                        if (cbEnableVoltageMonitoring.IsChecked == true)
                                            Title = "Chorus Lap Timer @ " + settings.SerialPortName + "(" + settings.SerialBaud + " Baud) Cell Voltage @ Device "+device+" :"+ cellVoltage.ToString("0.00") + " Volt";
                                    break;
                                case 'X': //All states corresponding to specified letters (see above) plus 'X' meaning the end of state transmission for each device
                                    if (device != 0)
                                    {
                                        SendData("R" + (device - 1) + "A");
                                    }
                                    else
                                    {
                                        aTimer.Interval = TimerCalibration;
                                        aTimer.Enabled = true;
                                        SendData("R*I");
                                    }
                                    break;
                            }
                            break;

                    }
                    readbuffer = "";
                }
                else
                {
                    readbuffer += indata[i];
                }
            }
        }

        #endregion

        private void contender_slider1_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (contenders1 != null)
                contenders1.Text = e.NewValue.ToString();
        }
        private void contender_slider2_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (contenders2 != null)
                contenders2.Text = e.NewValue.ToString();
        }

        private void SkipFirstLap_CLick(object sender, RoutedEventArgs e)
        {
            if (cbSkipFirstLap.IsChecked.Value)
            {
                SendData("R*F");
            }
            else
            {
                SendData("R*F");
            }
        }

        private void cbVoltageMonitoring_SelChange(object sender, SelectionChangedEventArgs e)
        {
            SendVoltageMonitorRequest("");
        }

        private void VoltageMonitoring_CLick(object sender, RoutedEventArgs e)
        {
            if (cbEnableVoltageMonitoring.IsChecked.Value)
            {
                cbVoltageMonitoring.IsEnabled = true;
                VoltageMonitorTimer.Interval = 10000;
                VoltageMonitorTimer.Enabled = true;
                SendVoltageMonitorRequest("");
            }
            else
            {
                cbVoltageMonitoring.IsEnabled = false;
                VoltageMonitorTimer.Enabled = false;
                Title = "Chorus Lap Timer @ " + settings.SerialPortName + "(" + settings.SerialBaud + " Baud)";
            }
        }

        private void SendVoltageMonitorRequest(string outdata)
        {
            if (!IsRaceActive)
            {
                SendData("R" + cbVoltageMonitoring.SelectedIndex + "Y");
            }
        }

        private void VoltageMonitorTimerEvent(object source, ElapsedEventArgs e)
        {
            if (!IsRaceActive)
                Dispatcher.Invoke(DispatcherPriority.Send, new UpdateUiTextDelegate(SendVoltageMonitorRequest), "");
        }

        private void Settings_TabControl_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            TabControl tabcontrol = (TabControl)sender;
            if ((tabcontrol.SelectedIndex - 2) >= 0)
            {
                if (!IsRaceActive)
                    SendData("R" + (tabcontrol.SelectedIndex - 2) + "Y");
            }
        }


        private void button_Click(object sender, RoutedEventArgs e)
        {
            //TODO
            SendData("R*v");
            SendData("R*R"); // start Race

            /* Disable when race started
            for (int i =0;i< DeviceCount; i++)
                ChorusDevices[i].grid.IsEnabled = false;
            Pilots_dataGrid.IsEnabled = false;
            RaceSettingsGrid.IsEnabled = false;
            textBox.IsEnabled=false;
            */
            /* Enable when race started
            for (int i =0;i< DeviceCount; i++)
                ChorusDevices[i].grid.IsEnabled = true;
            Pilots_dataGrid.IsEnabled = true;
            RaceSettingsGrid.IsEnabled = true;
            textBox.IsEnabled=true;
            */
        }

        public void TriggerLap(int device, int lap, int milliseconds)
        {
            ChorusDevices[device].LapTimes.Items.Add(new { Lap = lap.ToString(), Time = milliseconds.ToString() });
            //TODO
        }

    }
}
