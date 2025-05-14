import sys
import numpy as np
import pandas as pd
import seaborn as sns
import matplotlib
import matplotlib.pyplot as plt
from matplotlib.colors import LogNorm

top_k = 0 if len(sys.argv) == 1 else int(sys.argv[1])

df = pd.read_csv("datasets/processed_poi.csv")

df.sort_values("POI_count", ascending=False, inplace=True)


i = 0
for index, row in df.iterrows():
    row["POI_count"] = 0
    i += 1
    if i == top_k:
        break

x_vals = df["x"]
y_vals = df["y"]

# Setup image array and set values into it from "grumpiness" column
heatmap = np.zeros((201, 201))
heatmap[x_vals, y_vals] = df["POI_count"]
cmap = matplotlib.colormaps["inferno"]
cmap.set_bad("black", 1.0)
sns.heatmap(heatmap, norm=LogNorm(vmax=2479), cmap=cmap)
plt.title(f"Point of Interest Heatmap (jammed top {top_k} cells)")
plt.tight_layout()
plt.savefig(
    f"plots/poi_heatmap_missing_top_{top_k}.pdf", format="pdf", bbox_inches="tight"
)
plt.savefig(f"plots/poi_heatmap_missing_top_{top_k}.png", bbox_inches="tight")
