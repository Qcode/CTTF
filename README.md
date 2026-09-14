# Cache to the Future (CTTF)

This repository contains the artifact for Cache to the Future, a system to cache and deliver static content hosted on the web during internet blackouts. The artifact consists of two components: a simulation framework used to evaluate the system's design, and an Android prototype for microbenchmarks and peer-to-peer exchanges.

## Simulation Framework

### `model/`

The `model/` directory contains the simulation logic:

- **`config.py`** — The `Config` dataclass defines simulation parameters
- **`user.py`** — `User` class that tracks each user's preferences, computed preferences (learned from encounters), stored pages, page requests, and forwarding state.
- **`ratings.py`** — Functions for generating ratings and sampling pages from those ratings.
- **`simulation.py`** — The simulation phases:
  - `simulate_pre_blackout`: Users at the same (x, y, t) cell exchange ratings
  - `simulate_pre_blackout_stalking`: Variant where adversaries stalk leech users.
  - `simulate_post_blackout`: Users generate page requests and query nearby peers for them.
  - `simulate_epidemic_routing`: Alternative strategy where requests and responses propagate through the mesh network.
  - `generate_days`: Generates random grid-based mobility data.
- **`util.py`** — `get_default_config()` returns the baseline configuration.

### `simulation.py`

Runs a full simulation, serializing state to `data/runs/<name>`.

```bash
cd simulations
pip install -r requirements.txt
```

The simulations using the YJMob100K mobility dataset (the default) require the dataset files in `simulations/datasets/`. These are not included in the repository. To set up:

1. Download the YJMob100K dataset from [Zenodo](https://zenodo.org/records/10142719)
2. Place the CSV file in `simulations/datasets/` (e.g., `datasets/yjmob100k-dataset2.csv`)
3. Run `interpolation.ipynb` to generate the interpolated dataset (`yjmob100k-dataset2-interpolated.csv`), which is used by default

The grid-based simulation (`ModelType.GRID`) generates synthetic mobility data and does not require an external dataset.

```bash
python3 simulation.py                    # Run with default config
python3 paper_simulations.py             # Run all paper experiments
```

### `paper_simulations.py`

Defines all experiment configurations from the paper and launches them as parallel subprocesses.
Will require a very computationally powerful machine to run them all concurrently, we had 8 Intel Xeon Platinum 8276 CPUs and 6TB of RAM. 

### Notebooks

The Jupyter notebooks load results and produce figures. They all expect completed simulation runs in `data/runs/`.

- **`Analyze Run.ipynb`** — Figures for a single simulation run (Figure 5).

- **`across_runs.ipynb`** — Figures across multiple simulation (Figures 6-16). The script may need modification to load the correct data within the initial Jupyter cells.


## Android Prototype

### Overview

The Android app provides a simple proof-of-concept for caching pages, discovering nearby peers, and exchanging cached content and page requests.
It also contains implementations for microbenchmarks evaluated for the paper.

### Limitations

The following features from the paper's design were not evaluated directly, and hence weren't implemented as part of the prototype. 

- Fetching via proxy and validating signatures
- Page rating/exchange and background caching
- PoWs within exchanges

The prototype serves as a proof-of-concept for peer-to-peer communication and Bluetooth data exchange.
A real-world deployment of CTTF would require further engineering and monitoring.