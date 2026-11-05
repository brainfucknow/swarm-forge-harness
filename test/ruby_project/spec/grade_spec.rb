require_relative "../lib/grade"

RSpec.describe Grade do
  it "grades a score" do
    expect(Grade.letter(95)).to eq("A")
    expect(Grade.letter(85)).to eq("B")
    expect(Grade.letter(10)).to eq("F")
  end

  it "passes at 70" do
    expect(Grade.passing?(70)).to be(true)
  end
end
