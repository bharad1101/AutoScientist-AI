import {
  Chart as ChartJS,
  // Controllers
  BarController,
  LineController,
  ScatterController,
  DoughnutController,
  PieController,
  // Scales
  CategoryScale,
  LinearScale,
  LogarithmicScale,
  TimeScale,
  // Elements
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  // Plugins
  Filler,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';

// Register every chart.js controller and element once for the whole app so
// individual pages can use Bar / Line / Scatter / Doughnut without controller errors.
ChartJS.register(
  BarController,
  LineController,
  ScatterController,
  DoughnutController,
  PieController,
  CategoryScale,
  LinearScale,
  LogarithmicScale,
  TimeScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  Filler,
  Title,
  Tooltip,
  Legend
);

export default ChartJS;
