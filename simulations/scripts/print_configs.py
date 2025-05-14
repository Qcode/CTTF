import os
import pickle

for run in sorted(os.listdir("data/runs")):
    if run == ".DS_Store":
        continue
    with open(f"data/runs/{run}/config", "rb") as f:
        config = pickle.load(f)
        if not hasattr(config, "JAM_TOP_K_LOCATIONS"):
            config.JAM_TOP_K_LOCATIONS = 0
        print(f"RUN is {run}")
        print(config)
