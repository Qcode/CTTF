import matplotlib.pyplot as plt
from tqdm import tqdm
import numpy as np
import pickle

runs = [1, 2, 8, 9]

for run in runs:
    print(run)
    with open(f"data/runs/{run}/config", "rb") as f:
        config = pickle.load(f)
    with open(f"data/runs/{run}/users", "rb") as f:
        users = pickle.load(f)

    satisfied_at_time = np.zeros(len(config.POSTBLACKOUT_DAYS) * 48)
    requests_with_interaction = 0
    for user in tqdm(users):
        for request in user.requested_pages:
            if request.has_interacted:
                requests_with_interaction += 1
            if request.is_resolved():
                satisfied_at_time[
                    (request.ended_day - config.POSTBLACKOUT_DAYS[0]) * 48
                    + request.ended_timestep
                ] += 1
    plt.plot(
        np.linspace(
            0, len(config.POSTBLACKOUT_DAYS), len(config.POSTBLACKOUT_DAYS) * 48
        ),
        np.cumsum(satisfied_at_time) / requests_with_interaction,
        label=f"{len(config.PREBLACKOUT_DAYS)} days pre-blackout",
    )

plt.xlabel("Day")
plt.ylabel("Percent requests satisfied")
plt.title("Preblackout Length Effect on Efficacy")
plt.legend()
plt.savefig("plots/preblackout-length.pdf", format="pdf", bbox_inches="tight")
plt.show()
