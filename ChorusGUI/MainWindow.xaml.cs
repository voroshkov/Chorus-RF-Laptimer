using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using System.IO.Ports;

namespace chorusgui
{
    /// <summary>
    /// Interaction logic for Startup.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
            string[] ports = SerialPort.GetPortNames();
            if (ports.Count() == 0)
            {
                MessageBox.Show("NO SERIAL PORTS FOUND", "ERROR", MessageBoxButton.OK, MessageBoxImage.Error);
                Close();
            }
            foreach (string port in ports)
            {

                Button newBtn = new Button();
                newBtn.Content = "Select Port: " + port;
                newBtn.Name = port;
                newBtn.FontSize = 20;
                newBtn.Click += SelectPort;
                sp.Children.Add(newBtn);
            }
            
        }
        private void SelectPort(object sender, RoutedEventArgs e)
        {

            string[] bauds = comboBox.Text.Split(' ');
            Button Btn = (Button)sender;
            ChorusGUI GUI = new ChorusGUI();
            GUI.SetSerialPort(Btn.Name, int.Parse(bauds[0]));
            GUI.Show();
            Close();
        }
    }
}


