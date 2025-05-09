import numpy as np
import matplotlib.pyplot as plt
from model.ratings import (
    get_page_probability,
    zipf_to_ranking,
    ranking_to_zipf,
    noise_preferences,
)
from plot_scripts.util import get_config, make_plot_dir

config, the_run = get_config()
make_plot_dir(the_run)

the_zipf = get_page_probability(config.PAGE_COUNT)
the_truth_rankings = zipf_to_ranking(the_zipf)
the_truth_probability = the_zipf / np.sum(the_zipf)
the_truth_cdf = np.cumsum(the_truth_probability)


print("CDF metrics")
print(f"1-st page {the_truth_cdf[0]:.2%}")
print(f"10-th page {the_truth_cdf[9]:.2%}")
print(f"100-th page {the_truth_cdf[99]:.2%}")
print(f"10,000-th page {the_truth_cdf[9999]:.2%}")
print(f"1mil page {the_truth_cdf[10**6 - 1]:.2%}")
print("Corresponding popularity metrics")
print("1-st page", the_truth_rankings[0])
print("10-th page", the_truth_rankings[9])
print("100-th page", the_truth_rankings[99])
print("10,000-th page", the_truth_rankings[9999])
print("1mil page", the_truth_rankings[10**6 - 1])

print("---REVERSED---")
reversed_zipf = ranking_to_zipf(the_truth_rankings)
reversed_probability = reversed_zipf / np.sum(reversed_zipf)
reversed_cdf = np.cumsum(reversed_probability)
print(f"1-st page {reversed_cdf[0]:.2%}")
print(f"10-th page {reversed_cdf[9]:.2%}")
print(f"100-th page {reversed_cdf[99]:.2%}")
print(f"10,000-th page {reversed_cdf[9999]:.2%}")


plt.title("Page Ratings")
plt.ylabel("Rating")
plt.xlabel("Page Index")
plt.plot(
    np.arange(0, config.PAGE_COUNT),
    noise_preferences(config, the_truth_rankings),
    label="User",
)
plt.plot(np.arange(0, config.PAGE_COUNT), the_truth_rankings, label="Global")
plt.legend()
plt.savefig(f"plots/{the_run}/page-popularity.png")

noise_example = ranking_to_zipf(noise_preferences(config, the_truth_rankings))
noise_example /= np.sum(noise_example)
print(np.cumsum(noise_example))
plt.figure()
plt.title("Cumulative Distribution")
plt.plot(np.arange(0, config.PAGE_COUNT), the_truth_cdf, label="Ground Truth")
plt.plot(np.arange(0, config.PAGE_COUNT), np.cumsum(noise_example), label="Noised")
plt.legend()
plt.savefig(f"plots/{the_run}/truth-v-noise-cumulative-dist.png")
plt.show()
