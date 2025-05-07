import sys
import os
import pickle
from tqdm import tqdm
import numpy as np
from scipy.sparse import csr_array
import matplotlib.pyplot as plt
from model.ratings import (
    get_page_probability,
    zipf_to_ranking,
    ranking_to_zipf,
    noise_preferences,
)
from model.user import get_user_designations, UserType

runs = list(filter(lambda name: name != ".DS_Store", os.listdir("data/runs")))

the_run = sys.argv[1] if len(sys.argv) > 1 else max(map(int, runs))
with open(f"data/runs/{the_run}/config", "rb") as f:
    config = pickle.load(f)

print("CONFIG")
print(config)

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
plt.savefig(
    f"plots/page-popularity-c{config.PAGE_COUNT}-n{config.INDIVIDUAL_NOISE}.png"
)
plt.show(block=False)


noise_example = ranking_to_zipf(noise_preferences(config, the_truth_rankings))
noise_example /= np.sum(noise_example)
print(np.cumsum(noise_example))
plt.figure()
plt.title("Cumulative Distribution")
plt.plot(np.arange(0, config.PAGE_COUNT), the_truth_cdf, label="Ground Truth")
plt.plot(np.arange(0, config.PAGE_COUNT), np.cumsum(noise_example), label="Noised")
plt.legend()
plt.show(block=False)

print("LOADING USERS")

with open(f"data/runs/{the_run}/users", "rb") as f:
    users = pickle.load(f)

user_designations = get_user_designations(config)

all_preferences = csr_array((1, config.PAGE_COUNT))

for i in range(config.TOTAL_USERS):
    if users[i].user_type == UserType.NORMAL:
        all_preferences += users[i].preferences

print("Nonzero preferences:", all_preferences.count_nonzero())


overall_probabilities = np.zeros_like(users[0].computed_preferences)
leeches_with_interactions = 0
average_pages_seen = 0
average_nonzero_probs = 0

stored_set = set()

for i in tqdm(range(config.TOTAL_USERS)):
    if (
        users[i].user_type == UserType.LEECH
        and users[i].computed_preferences is not None
    ):
        leeches_with_interactions += 1
        overall_probabilities += users[i].computed_preferences
        average_pages_seen += users[i].computed_preferences.size
    stored_set.update(users[i].stored_pages)

print(f"Stored pages across all users {len(stored_set)}")

print("How many leeches interacted with an active user", leeches_with_interactions)
print("Total leeches", config.NUM_LEECH)
if leeches_with_interactions > 0:
    overall_probabilities /= leeches_with_interactions
    print(
        "Average leech rankings collected",
        average_pages_seen / leeches_with_interactions,
    )

    param_string = (
        f"overall-a{config.NUM_ADVERSARY}-am{config.ADVERSARY_FORCE_MULTIPLIER}-sNONE"
    )
    np.save(f"data/leech/{param_string}", overall_probabilities)

    plt.title(
        f"regular={config.NUM_REGULAR/config.TOTAL_USERS}, adversary = {config.NUM_ADVERSARY/config.TOTAL_USERS}, multiplier = {config.ADVERSARY_FORCE_MULTIPLIER}"
    )
    plt.ylabel("Ranking")
    plt.xlabel("Page Index")
    plt.plot(np.arange(0, config.PAGE_COUNT), overall_probabilities.toarray().flatten())
    plt.savefig(f"plots/{param_string}.png", bbox_inches="tight")
    plt.show(block=False)

    plt.figure()
    plt.title("Cumulative Distribution")
    zipf = ranking_to_zipf(overall_probabilities.toarray().flatten())
    zipf /= np.sum(zipf)
    plt.plot(np.arange(0, config.PAGE_COUNT), np.cumsum(zipf))
    plt.show(block=False)
else:
    print("No leeches with interactions")

rated_by_someone = all_preferences.nonzero()[1]
print(f"Number of pages rated by someone {rated_by_someone}")

total_requests = 0
total_resolved = 0
total_storing_first = 0
total_with_interactions = 0

total_was_stored = 0

user_appearance_count = 0

requests = np.zeros(config.PAGE_COUNT)
satisfied = np.zeros(config.PAGE_COUNT)

satisfied_by_time_initiated = np.zeros(48)
total_by_time_initiated = np.zeros(48)

total_unsatisfied = 0
unsatisfied_by_index = np.zeros(config.PAGE_COUNT)

total_satisfied = 0
satisfied_by_index = np.zeros(config.PAGE_COUNT)

satisfied_in_x_timesteps = np.zeros(48)

satisfied_by_time = np.zeros(61 * 48)

for user in users:
    if 0 in user.stored_pages:
        total_storing_first += 1
    total_requests += len(user.requested_pages)
    for request in user.requested_pages:
        requests[request.index] += 1
        if request.has_interacted:
            total_with_interactions += 1
            total_by_time_initiated[request.started_timestep] += 1
        if request.is_resolved():
            # print(request.index)
            total_resolved += 1
            satisfied[request.index] += 1
            satisfied_by_time_initiated[request.started_timestep] += 1
            # print(request.started_timestep)
            # print(request.ended_timestep)
            satisfied_by_time[
                (request.ended_day - 1) * 48 + request.ended_timestep
            ] += 1
        if request.has_interacted and not request.is_resolved():
            total_unsatisfied += 1
            unsatisfied_by_index[request.index] += 1
        if request.has_interacted and request.is_resolved():
            total_satisfied += 1
            satisfied_by_index[request.index] += 1

            satisfied_in_x_timesteps[
                request.ended_timestep - request.started_timestep
            ] += 1
        if request.has_interacted and request.index in stored_set:
            total_was_stored += 1

percent_satisfied_by_time_initiated = (
    satisfied_by_time_initiated / total_by_time_initiated
)

print("Total Requests", total_requests)
print("Total Resolved", total_resolved)
print("Total with interactions", total_with_interactions)
print("Total users storing index 0", total_storing_first)

print("Stored by someone and did interact", total_was_stored)

plt.title("Requests made by index")
plt.plot(np.arange(0, config.PAGE_COUNT), requests, label="Requests made")
plt.plot(np.arange(0, config.PAGE_COUNT), satisfied, label="Requests satisfied")
plt.legend()

plt.figure()
plt.title("Percent satsified over days")
plt.plot(
    np.linspace(0, 61, 61 * 48), np.cumsum(satisfied_by_time) / total_with_interactions
)
plt.xlabel("Day")
plt.ylabel("Percent of requests satisfied")

plt.figure()
plt.title("Percent satisfied")
plt.plot(np.arange(0, 10000), (satisfied / np.maximum(1, requests))[:10000])

plt.figure()
plt.title("Percent satisfied")
plt.plot(np.arange(0, config.PAGE_COUNT), (satisfied / np.maximum(1, requests)))


plt.figure()
plt.title("Percent satisfied by time initiated")
plt.plot(np.arange(0, 48), percent_satisfied_by_time_initiated)
plt.xlabel("Half-hour timestep")
plt.ylabel("Percent satisfied")
plt.savefig("percent_satisfied_by_time_initiated.png")

plt.figure()
plt.title("Cumulative unsatisfied percentage")
plt.plot(
    np.arange(0, config.PAGE_COUNT), np.cumsum(unsatisfied_by_index) / total_unsatisfied
)
plt.ylabel("Percent Unsatisfied")
plt.xlabel("Page index")
plt.savefig("cumulative_unsatisfied_percentage.png")


plt.figure()
plt.title("Cumulative satisfied percentage")
plt.plot(
    np.arange(0, config.PAGE_COUNT), np.cumsum(satisfied_by_index) / total_satisfied
)

plt.figure()
plt.title("Hours to resolve page request")
frequencies_24 = satisfied_in_x_timesteps.reshape(24, 2).sum(axis=1)
plt.xlabel("Hours")
plt.ylabel("Number resolved")

# Plot the histogram
plt.bar(np.arange(0, 24), frequencies_24, width=1, align="edge", edgecolor="black")

# Labels and title
plt.xticks(np.arange(0, 24))  # Label all bins

plt.savefig("hours_to_resolve_page_request.png")

# Show the plot
plt.show()
