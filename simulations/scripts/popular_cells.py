import numpy as np
import pandas as pd

counts = np.zeros((200, 200))
df = pd.read_csv("datasets/cell_POIcat.csv")
series = df.groupby(["x", "y"])["POI_count"].sum()
series.to_csv("datasets/processed_poi.csv")
