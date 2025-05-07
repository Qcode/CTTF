import matplotlib.pyplot as plt

other_x = [0, 0.1, 0.2, 0.3, 0.4, 0.5]
uniform_rating_100_y = [176820, 213128, 248914, 281640, 312966, 342955]

uniform_rating_200_x = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.75, 0.9, 1]
uniform_rating_200_y = [
    295570,
    359328,
    417206,
    469042,
    516461,
    559005,
    648576,
    690881,
    713947,
]

uniform_rating_300_y = [389426, 471994, 543936, 605255, 657150, 701775]

uniform_rating_400_y = [467584, 562291, 640060, 703786, 755514, 797948]

uniform_rating_500_y = [532555, 635043, 714148, 777297, 825036, 862919]

uniform_rating_750_y = [657927, 764723, 839691, 889186, 924131, 947271]

uniform_rating_1000_y = [747171, 847282, 908002, 944470, 966406, 979375]

plt.plot(other_x, uniform_rating_100_y, marker="o", label="100 pages ranked")
plt.plot(
    uniform_rating_200_x, uniform_rating_200_y, marker="o", label="200 pages ranked"
)
plt.plot(other_x, uniform_rating_300_y, marker="o", label="300 pages ranked")
plt.plot(other_x, uniform_rating_400_y, marker="o", label="400 pages ranked")
plt.plot(other_x, uniform_rating_500_y, marker="o", label="500 pages ranked")
plt.plot(other_x, uniform_rating_750_y, marker="o", label="750 pages ranked")
plt.plot(other_x, uniform_rating_1000_y, marker="o", label="1000 pages ranked")
plt.title("Uniform randomness vs overall pages ranked (6250 proactive users)")
plt.xlabel("Uniform randomness percent")
plt.ylabel("Pages ranked across all users")
plt.legend()
plt.show()
