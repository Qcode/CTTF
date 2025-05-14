import pickle
import numpy as np
import matplotlib.pyplot as plt
from model.ratings import (
    ranking_to_zipf,
)
from model.user import get_user_designations
from plot_scripts.util import (
    get_config,
    make_plot_dir,
    get_nonzero_preferences,
    get_stored_across_all,
    get_leech_info,
    get_total_requests,
    get_total_resolved,
    get_total_with_interactions,
)

config, the_run = get_config()
make_plot_dir(the_run)

print("LOADING USERS")
with open(f"data/runs/{the_run}/users", "rb") as f:
    users = pickle.load(f)

user_designations = get_user_designations(config)

print(
    "Nonzero Preferences Across Proactive Users", get_nonzero_preferences(config, users)
)

stored_set = get_stored_across_all(config, users)
print(f"Stored pages across all users {len(stored_set)}")

average_leech_probabilities, leeches_with_interactions, average_pages_seen = (
    get_leech_info(config, users)
)

print("How many leeches interacted with an active user", leeches_with_interactions)
print("Total leeches", config.NUM_LEECH)
print(
    "Average leech rankings collected",
    average_pages_seen,
)
plt.title(
    f"regular={config.NUM_REGULAR/config.TOTAL_USERS}, adversary = {config.NUM_ADVERSARY/config.TOTAL_USERS}, multiplier = {config.ADVERSARY_FORCE_MULTIPLIER}"
)
plt.ylabel("Ranking")
plt.xlabel("Page Index")
plt.plot(
    np.arange(0, config.PAGE_COUNT), average_leech_probabilities.toarray().flatten()
)
plt.savefig(f"plots/{the_run}/average_leech_probs.png", bbox_inches="tight")

plt.figure()
plt.title("Cumulative Distribution")
zipf = ranking_to_zipf(average_leech_probabilities.toarray().flatten())
zipf /= np.sum(zipf)
plt.plot(np.arange(0, config.PAGE_COUNT), np.cumsum(zipf))
plt.savefig(f"plots/{the_run}/leech_probs_cumulative_dist.png")

print("Total Requests", get_total_requests(users))
print("Total Resolved", get_total_resolved(users))
print("Total with interactions", get_total_with_interactions(users))

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

num_days = len(config.PREBLACKOUT_DAYS) + len(config.POSTBLACKOUT_DAYS)
num_initiating_days = config.REQUEST_CUTOFF_DAY - config.POSTBLACKOUT_DAYS[0]
if config.REQUEST_CUTOFF_TIMESTEP > 0:
    num_initiating_days += 1

initiated_on_day = np.zeros(num_initiating_days)
satisfied_by_time = np.zeros((num_initiating_days, len(config.POSTBLACKOUT_DAYS) * 48))

for user in users:
    for request in user.requested_pages:
        requests[request.index] += 1
        if request.has_interacted:
            total_by_time_initiated[request.started_timestep] += 1
            initiated_on_day[
                request.started_day - len(config.PREBLACKOUT_DAYS) - 1
            ] += 1
        if request.is_resolved():
            # print(request.index)
            satisfied[request.index] += 1
            satisfied_by_time_initiated[request.started_timestep] += 1
            # print(request.started_timestep)
            # print(request.ended_timestep)
            satisfied_by_time[request.started_day - config.POSTBLACKOUT_DAYS[0]][
                (request.ended_day - config.POSTBLACKOUT_DAYS[0]) * 48
                + request.ended_timestep
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


print("Stored by someone and did interact", total_was_stored)

plt.figure()
plt.title("Requests satisfied over time")
for i in range(num_initiating_days):
    plt.plot(
        np.linspace(
            i,
            len(config.POSTBLACKOUT_DAYS),
            (len(config.POSTBLACKOUT_DAYS) - i) * 48,
        ),
        np.cumsum(satisfied_by_time[i][i * 48 :]) / initiated_on_day[i],
        label=f"Requests initiated on blackout day {i}",
    )
plt.xlabel("Blackout day")
plt.ylabel("Percent satisfied")
plt.legend()


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
plt.show()
