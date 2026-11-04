module Grade
  def self.letter(score)
    if score >= 90
      "A"
    elsif score >= 80
      "B"
    elsif score >= 70
      "C"
    else
      "F"
    end
  end

  def self.passing?(score)
    letter(score) != "F"
  end
end
